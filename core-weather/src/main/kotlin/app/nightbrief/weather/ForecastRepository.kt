package app.nightbrief.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface ForecastCache {
    suspend fun get(key: String): Forecast?
    suspend fun put(key: String, forecast: Forecast)
}

class InMemoryForecastCache : ForecastCache {
    private val map = ConcurrentHashMap<String, Forecast>()
    override suspend fun get(key: String): Forecast? = map[key]
    override suspend fun put(key: String, forecast: Forecast) {
        map[key] = forecast
    }
}

/**
 * Stores one JSON file per location so the last good forecast survives process death and offline use.
 *
 * Puts share one temp file per key, so they take a lock. A failed rename writes the text already
 * in memory. [IOException] means the directory could not be created or the file could not be written;
 * the caller still has the forecast and should not treat that as a failed fetch.
 */
class FileForecastCache(
    private val directory: File,
    private val maxMemoryEntries: Int = DEFAULT_MEMORY_ENTRIES,
) : ForecastCache {
    init {
        require(maxMemoryEntries >= 0) { "maxMemoryEntries must be non-negative: $maxMemoryEntries" }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()
    private val memoryLock = Any()
    private val memory = object : LinkedHashMap<String, Forecast>(maxMemoryEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Forecast>): Boolean =
            size > maxMemoryEntries
    }

    override suspend fun get(key: String): Forecast? {
        if (maxMemoryEntries > 0) {
            synchronized(memoryLock) { memory[key] }?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            val file = File(directory, "$key.json")
            if (!file.exists()) return@withContext null
            val decoded = runCatching { json.decodeFromString(Forecast.serializer(), file.readText()) }.getOrNull()
            if (decoded != null && maxMemoryEntries > 0) {
                synchronized(memoryLock) {
                    val existing = memory[key]
                    if (existing == null || decoded.fetchedAtEpochSecond >= existing.fetchedAtEpochSecond) {
                        memory[key] = decoded
                    }
                }
            }
            decoded
        }
    }

    override suspend fun put(key: String, forecast: Forecast) {
        val text = json.encodeToString(Forecast.serializer(), forecast)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("could not create forecast cache directory ${directory.path}")
                }
                val dest = File(directory, "$key.json")
                val tmp = File(directory, "$key.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(dest)) {
                    dest.writeText(text)
                    tmp.delete()
                }
            }
            if (maxMemoryEntries > 0) {
                synchronized(memoryLock) { memory[key] = forecast }
            }
        }
    }

    companion object {
        const val DEFAULT_MEMORY_ENTRIES = 32
    }
}

enum class ForecastStatus {
    /** Fetched just now. */
    FRESH,
    /** Served from cache because it is recent enough. */
    CACHED,
    /** Network fetch failed; serving the last cached forecast, however old. */
    STALE,
}

data class ForecastResult(
    val forecast: Forecast,
    val status: ForecastStatus,
    /** Non-fatal problems, e.g. 7Timer unavailable or the network fetch that forced a stale fallback. */
    val warnings: List<String> = emptyList(),
)

interface ForecastSource {
    /** Throws [WeatherApiException] when no forecast (fresh or cached) is available. */
    suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean = false): ForecastResult

    /**
     * Fetches forecasts for multiple coordinates, returning one [Result] per coordinate in order.
     * Repositories may batch Open-Meteo calls for coordinates sharing a [WeatherModel].
     */
    suspend fun forecastBatch(
        coords: List<Pair<Double, Double>>,
        forceRefresh: Boolean = false,
    ): List<Result<ForecastResult>> = coroutineScope {
        coords.map { (lat, lon) ->
            async {
                try {
                    Result.success(forecast(lat, lon, forceRefresh))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
    }
}

/**
 * One file per location. A file younger than [maxCacheAge] is served with no network call
 * unless refresh is forced. After that the network is tried and a success replaces the file.
 * Calls for the same cache cell share one in-flight fetch.
 * A failed fetch serves the last file at any age as [ForecastStatus.STALE]. No file still throws.
 * HTTP 429 records a short cooldown: until it passes, a later call serves that file (or throws
 * [ForecastProblem.BUSY]) and does not hit the network again, unless refresh is forced.
 * A forecast that was fetched but could not be written to disk is still returned as [ForecastStatus.FRESH].
 */
class ForecastRepository(
    private val openMeteo: OpenMeteoClient = OpenMeteoClient(),
    private val sevenTimer: SevenTimerClient = SevenTimerClient(),
    private val cache: ForecastCache = InMemoryForecastCache(),
    private val clock: Clock = Clock.systemUTC(),
    private val maxCacheAge: Duration = Duration.ofMinutes(60),
    private val busyCooldown: Duration = Duration.ofSeconds(30),
    private val flightScope: CoroutineScope? = null,
) : ForecastSource {
    private val flights = ConcurrentHashMap<String, Deferred<ForecastResult>>()
    private val busyUntil = ConcurrentHashMap<String, Instant>()

    override suspend fun forecast(latitude: Double, longitude: Double, forceRefresh: Boolean): ForecastResult {
        val key = cacheKey(latitude, longitude)
        while (true) {
            val cached = cache.get(key)
            val age = cached?.let { Duration.between(it.fetchedAt, clock.instant()) }
            if (!forceRefresh && cached != null && age != null && age < maxCacheAge) {
                return ForecastResult(cached, ForecastStatus.CACHED)
            }
            val quietUntil = busyUntil[key]
            if (!forceRefresh && quietUntil != null && clock.instant().isBefore(quietUntil)) {
                if (cached != null) {
                    return ForecastResult(cached, ForecastStatus.STALE, listOf(ForecastProblem.BUSY.userText()))
                }
                throw WeatherApiException("HTTP 429", problem = ForecastProblem.BUSY)
            }

            val created = CompletableDeferred<ForecastResult>()
            val inFlight = flights.putIfAbsent(key, created)
            if (inFlight != null) {
                try {
                    return inFlight.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }

            val detached = flightScope?.takeIf { it.isActive }
            if (detached != null) {
                val job = detached.launch {
                    try {
                        val result = load(latitude, longitude, key, cached)
                        flights.remove(key, created)
                        created.complete(result)
                    } catch (e: Throwable) {
                        flights.remove(key, created)
                        created.completeExceptionally(e)
                    }
                }
                job.invokeOnCompletion { cause ->
                    flights.remove(key, created)
                    if (!created.isCompleted) {
                        created.completeExceptionally(cause ?: CancellationException("Forecast flight ended"))
                    }
                }
                try {
                    return created.await()
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }

            var removed = false
            return try {
                val result = load(latitude, longitude, key, cached)
                flights.remove(key, created)
                removed = true
                created.complete(result)
                result
            } catch (e: Throwable) {
                flights.remove(key, created)
                removed = true
                created.completeExceptionally(e)
                throw e
            } finally {
                if (!removed) flights.remove(key, created)
            }
        }
    }

    override suspend fun forecastBatch(
        coords: List<Pair<Double, Double>>,
        forceRefresh: Boolean,
    ): List<Result<ForecastResult>> {
        if (coords.isEmpty()) return emptyList()
        val keys = coords.map { (lat, lon) -> cacheKey(lat, lon) }
        val uniqueEntries = LinkedHashMap<String, Pair<Double, Double>>()
        for (i in coords.indices) {
            uniqueEntries.putIfAbsent(keys[i], coords[i])
        }
        if (uniqueEntries.size <= 1) {
            return super.forecastBatch(coords, forceRefresh)
        }

        val resolved = LinkedHashMap<String, Result<ForecastResult>>()
        val toFetch = mutableListOf<BatchPending>()
        val now = clock.instant()

        for ((key, coord) in uniqueEntries) {
            val (lat, lon) = coord
            val cached = cache.get(key)
            val age = cached?.let { Duration.between(it.fetchedAt, now) }
            if (!forceRefresh && cached != null && age != null && age < maxCacheAge) {
                resolved[key] = Result.success(ForecastResult(cached, ForecastStatus.CACHED))
                continue
            }
            val quietUntil = busyUntil[key]
            if (!forceRefresh && quietUntil != null && now.isBefore(quietUntil)) {
                resolved[key] = if (cached != null) {
                    Result.success(ForecastResult(cached, ForecastStatus.STALE, listOf(ForecastProblem.BUSY.userText())))
                } else {
                    Result.failure(WeatherApiException("HTTP 429", problem = ForecastProblem.BUSY))
                }
                continue
            }
            toFetch += BatchPending(key, lat, lon, cached)
        }

        if (toFetch.isNotEmpty()) {
            val byModel = toFetch.groupBy { WeatherModel.forLocation(it.lat, it.lon) }
            val fetchedPairs = coroutineScope {
                byModel.map { (model, group) ->
                    async {
                        if (group.size == 1) {
                            val item = group.single()
                            val res = try {
                                Result.success(forecast(item.lat, item.lon, forceRefresh))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                            listOf(item.key to res)
                        } else {
                            fetchModelGroup(model, group)
                        }
                    }
                }.awaitAll().flatten()
            }
            for ((key, res) in fetchedPairs) {
                resolved[key] = res
            }
        }

        return keys.map { resolved.getValue(it) }
    }

    private suspend fun fetchModelGroup(
        model: WeatherModel,
        group: List<BatchPending>,
    ): List<Pair<String, Result<ForecastResult>>> = coroutineScope {
        val groupCoords = group.map { it.lat to it.lon }
        val astroDeferreds = group.map { item ->
            async { runCatching { sevenTimer.fetch(item.lat, item.lon) } }
        }
        val secondaryDeferred = async {
            runCatching {
                openMeteo.fetchBatch(
                    groupCoords,
                    model = WeatherModel.secondaryFor(model),
                    hourly = OpenMeteoClient.SECONDARY_HOURLY_FIELDS,
                )
            }
        }
        val primaryForecasts = try {
            openMeteo.fetchBatch(groupCoords, model = model)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WeatherApiException) {
            if (e.problem == ForecastProblem.BUSY) {
                val until = clock.instant().plus(busyCooldown)
                for (item in group) busyUntil[item.key] = until
            }
            return@coroutineScope group.map { item ->
                val res = if (item.cached != null) {
                    Result.success(ForecastResult(item.cached, ForecastStatus.STALE, listOf(e.userText())))
                } else {
                    Result.failure(e)
                }
                item.key to res
            }
        } catch (e: Exception) {
            return@coroutineScope group.map { item ->
                item.key to Result.failure(e)
            }
        }

        val secondaries = secondaryDeferred.await().getOrNull()
        val astros = astroDeferreds.awaitAll()
        group.mapIndexed { i, item ->
            val weather = primaryForecasts[i]
            val points = astros[i]
            val warnings = points.exceptionOrNull()?.let { listOf("Seeing and transparency unavailable") }.orEmpty()
            val merged = secondaries?.getOrNull(i)?.let { weather.withSecondary(it) } ?: weather
            val complete = merged.withSevenTimer(points.getOrDefault(emptyList()))
            try {
                cache.put(item.key, complete)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Disk failure must not drop a freshly fetched forecast.
            }
            busyUntil.remove(item.key)
            item.key to Result.success(ForecastResult(complete, ForecastStatus.FRESH, warnings))
        }
    }

    private data class BatchPending(val key: String, val lat: Double, val lon: Double, val cached: Forecast?)

    private suspend fun load(
        latitude: Double,
        longitude: Double,
        key: String,
        cached: Forecast?,
    ): ForecastResult = try {
        val (forecast, warnings) = fetch(latitude, longitude)
        try {
            cache.put(key, forecast)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // The night is already in hand. A disk failure must not blank Tonight.
        }
        busyUntil.remove(key)
        ForecastResult(forecast, ForecastStatus.FRESH, warnings)
    } catch (e: WeatherApiException) {
        if (e.problem == ForecastProblem.BUSY) {
            busyUntil[key] = clock.instant().plus(busyCooldown)
        }
        if (cached != null) {
            ForecastResult(cached, ForecastStatus.STALE, listOf(e.userText()))
        } else {
            throw e
        }
    }

    private suspend fun fetch(latitude: Double, longitude: Double): Pair<Forecast, List<String>> = coroutineScope {
        val astro = async { runCatching { sevenTimer.fetch(latitude, longitude) } }
        val primary = WeatherModel.forLocation(latitude, longitude)
        val second = async {
            runCatching {
                openMeteo.fetch(
                    latitude,
                    longitude,
                    model = WeatherModel.secondaryFor(primary),
                    hourly = OpenMeteoClient.SECONDARY_HOURLY_FIELDS,
                )
            }
        }
        val weather = openMeteo.fetch(latitude, longitude)
        val points = astro.await()
        val warnings = points.exceptionOrNull()?.let { listOf("Seeing and transparency unavailable") }.orEmpty()
        // A failed second model leaves cloudCoverSecondary null; the briefing still succeeds.
        val merged = second.await().getOrNull()?.let { weather.withSecondary(it) } ?: weather
        merged.withSevenTimer(points.getOrDefault(emptyList())) to warnings
    }

    companion object {
        /** ~1 km grid so tiny GPS jitter reuses the same cache entry. */
        fun cacheKey(latitude: Double, longitude: Double): String =
            String.format(Locale.ROOT, "fc_%.2f_%.2f", latitude, longitude)
    }
}
