package app.nightbrief.score

import app.nightbrief.astro.CometOrbit
import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPasses
import app.nightbrief.astro.MoonPhase
import app.nightbrief.astro.MpcComets
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEvents
import app.nightbrief.astro.Target
import app.nightbrief.gear.GearKit
import app.nightbrief.sites.Site
import app.nightbrief.weather.AirQualitySeries
import app.nightbrief.weather.AirQualitySource
import app.nightbrief.weather.CometElementsFile
import app.nightbrief.weather.CometSource
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastRepository
import app.nightbrief.weather.IssTle
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.TleSource
import app.nightbrief.weather.WeatherApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class OutlookNight(
    val date: LocalDate,
    val score: Int?,
    val moonIllumination: Double,
    val darkness: Darkness,
    val coverage: ForecastCoverage,
    /** True when any night-score factor was estimated (no 7Timer index, or a missing cloud value). */
    val estimated: Boolean = false,
    /** Measured clear cloud. False when cloud was estimated, so a full moon is not named clear. */
    val clearSkies: Boolean = false,
    /** True when the ephemeris phase is full. Illumination alone is not enough. */
    val fullMoon: Boolean = false,
    /** Shower adjective when it is near peak and worth watching, e.g. "Perseid". */
    val showerLabel: String? = null,
    /** Eclipse or opposition title from [app.nightbrief.astro.SkyEvents] on this date. */
    val skyEventTitle: String? = null,
    /**
     * True when this night has no forecast because the fetch failed.
     * Distinct from a saved forecast that simply does not cover the date.
     */
    val forecastMissing: Boolean = false,
) {
    val band: Band? get() = score?.let(Band::of)
}

data class WeeklyOutlook(val site: Site, val nights: List<OutlookNight>) {
    val best: OutlookNight? get() = nights.filter { it.score != null }.maxByOrNull { it.score!! }

    fun headline(locale: Locale = Locale.getDefault()): String {
        if (nights.isNotEmpty() && nights.all { it.forecastMissing }) return "Couldn't load the forecast"
        val b = best ?: return "No forecast available this week"
        val day = b.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return "Best night this week: $day (${b.score})"
    }
}

/** Results for all sites from a single round of forecast fetches. */
data class Briefing(
    val generatedAt: Instant,
    /** Tonight's report per site, in the order the sites were given. */
    val tonight: List<NightReport>,
    val outlooks: List<WeeklyOutlook>,
    /** Planetary Kp series used for [NightReport.aurora]. Null when SWPC was not asked or the fetch failed. */
    val kp: KpForecast? = null,
) {
    fun reportFor(siteId: String): NightReport? = tonight.firstOrNull { it.site.id == siteId }
    fun outlookFor(siteId: String): WeeklyOutlook? = outlooks.firstOrNull { it.site.id == siteId }
}

/** Scores nights for the UI and for background workers. Tests can supply a fake. */
interface BriefingSource {
    suspend fun brief(
        sites: List<Site>,
        kit: GearKit,
        outlookDays: Int = 7,
        forceRefresh: Boolean = false,
    ): Briefing

    suspend fun brief(
        sites: List<Site>,
        kit: GearKit,
        outlookDays: Int = 7,
        forceRefresh: Boolean = false,
        customTargets: List<Target>,
    ): Briefing = brief(sites, kit, outlookDays, forceRefresh)

    suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport

    suspend fun plan(
        site: Site,
        date: LocalDate,
        kit: GearKit,
        customTargets: List<Target>,
    ): NightReport = plan(site, date, kit)

    /**
     * Plans [site] for [date] with only what the score and coverage need: no aurora, ISS,
     * smoke, comet, or target suggestions. Darker-sky candidates render none of those, so
     * they use this instead of [plan]. The score itself is the same one [plan] publishes.
     */
    suspend fun planLean(site: Site, date: LocalDate, kit: GearKit): NightReport = plan(site, date, kit)
}

class BriefingService(
    private val forecasts: ForecastSource,
    private val clock: Clock = Clock.systemUTC(),
    /** Planetary Kp. Null leaves [NightReport.aurora] empty; a failed fetch does the same and does not fail the briefing. */
    private val kp: KpSource? = null,
    /** ISS elements. Null or a failed fetch leaves [NightReport.issPasses] empty and does not fail the briefing. */
    private val iss: TleSource? = null,
    /**
     * Open-Meteo air quality (CAMS). Null or a failed fetch leaves [NightReport.smoke] empty and
     * does not fail the briefing. Unlike Kp this is per site, so the brief fetches each saved site.
     */
    private val airQuality: AirQualitySource? = null,
    /**
     * MPC observable-comet elements. Null or a failed fetch leaves [NightReport.comet]
     * empty and does not fail the briefing.
     */
    private val comets: CometSource? = null,
    /**
     * Parses one MPC comet file into orbits. Injected so a test can count parses; a real
     * brief parses once per call, not once per site or night.
     */
    private val cometParser: (String) -> List<CometOrbit> = MpcComets::parse,
) : BriefingSource {
    override suspend fun brief(
        sites: List<Site>,
        kit: GearKit,
        outlookDays: Int,
        forceRefresh: Boolean,
    ): Briefing = brief(sites, kit, outlookDays, forceRefresh, emptyList())

    override suspend fun brief(
        sites: List<Site>,
        kit: GearKit,
        outlookDays: Int,
        forceRefresh: Boolean,
        customTargets: List<Target>,
    ): Briefing = coroutineScope {
        val now = clock.instant()
        val kpDeferred = async { fetchKp() }
        val issDeferred = async { fetchIss() }
        val cometDeferred = async { fetchComets() }
        val airQualityDeferred = async { fetchAirQualityForSites(sites) }
        // Sites in the same ~1 km cache cell share one fetch; ephemeris and scoring still run per site.
        val keys = sites.map { ForecastRepository.cacheKey(it.latitude, it.longitude) }
        val fetchedByKey = keys.distinct().map { key ->
            async {
                val site = sites[keys.indexOf(key)]
                key to fetchForecast(site, forceRefresh)
            }
        }.awaitAll().toMap()
        val fetched = sites.map { site ->
            site to fetchedByKey.getValue(ForecastRepository.cacheKey(site.latitude, site.longitude))
        }
        val kpForecast = kpDeferred.await()
        val issTle = issDeferred.await()
        val cometFile = cometDeferred.await()
        val airQualityBySite = airQualityDeferred.await()
        // One parse per brief, reused for every site's Tonight report.
        val cometOrbits = parseComets(cometFile)

        val tonight = mutableListOf<NightReport>()
        val outlooks = mutableListOf<WeeklyOutlook>()
        for ((site, result) in fetched) {
            val fr: ForecastResult? = result.getOrNull()
            val warnings = buildList {
                fr?.warnings?.let(::addAll)
                result.exceptionOrNull()?.let { add(forecastUserText(it)) }
            }
            val start = NightPlanner.tonight(now, site.zone)
            val skyEvents = skyEventsFor(start, outlookDays, site.zone)
            val nights = (0 until outlookDays).map { offset ->
                val planned = NightPlanner.plan(
                    site = site,
                    date = start.plusDays(offset.toLong()),
                    forecast = fr?.forecast,
                    kit = kit,
                    forecastStatus = fr?.status,
                    warnings = warnings,
                    includeSuggestions = offset == 0,
                    customTargets = customTargets,
                )
                // Aurora, ISS, smoke, and comets are Tonight-only cards. An OutlookNight keeps none
                // of them, so future nights skip the side products rather than enriching and discarding.
                if (offset == 0) {
                    planned.withAurora(kpForecast)
                        .withIss(issTle)
                        .withSmoke(airQualityBySite[site.id])
                        .withComet(cometOrbits)
                } else {
                    planned
                }
            }
            tonight += nights.first()
            outlooks += WeeklyOutlook(
                site,
                nights.map {
                    val onDate = skyEvents.filter { event ->
                        event.instant.atZone(site.zone).toLocalDate() == it.date
                    }
                    OutlookNight(
                        it.date,
                        it.scoreValue,
                        it.ephemeris.moonIllumination,
                        it.ephemeris.darkness,
                        it.coverage,
                        estimated = it.score?.factors?.any { factor -> factor.estimated } == true,
                        clearSkies = NightNames.clearSkies(it),
                        fullMoon = it.ephemeris.moonPhaseName == MoonPhase.FULL,
                        showerLabel = NightNames.showerLabel(it.meteor),
                        skyEventTitle = NightNames.eventTitle(onDate),
                        forecastMissing = fr == null,
                    )
                },
            )
        }
        Briefing(now, tonight, outlooks, kpForecast)
    }

    /** Plans a single site for a specific date (planning mode). */
    override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport =
        plan(site, date, kit, emptyList())

    override suspend fun plan(
        site: Site,
        date: LocalDate,
        kit: GearKit,
        customTargets: List<Target>,
    ): NightReport = coroutineScope {
        val kpDeferred = async { fetchKp() }
        val issDeferred = async { fetchIss() }
        val cometDeferred = async { fetchComets() }
        val airQualityDeferred = async { fetchAirQuality(site) }
        val forecastDeferred = async { fetchForecast(site, forceRefresh = false) }
        val kpForecast = kpDeferred.await()
        val issTle = issDeferred.await()
        val cometFile = cometDeferred.await()
        val airQuality = airQualityDeferred.await()
        val fr = forecastDeferred.await()
        val failure = fr.exceptionOrNull()?.let { forecastUserText(it) }
        NightPlanner.plan(
            site, date, fr.getOrNull()?.forecast, kit, fr.getOrNull()?.status,
            fr.getOrNull()?.warnings.orEmpty() + listOfNotNull(failure),
            customTargets = customTargets,
        ).withAurora(kpForecast).withIss(issTle).withSmoke(airQuality).withComet(parseComets(cometFile))
    }

    /** Forecast only, no side products and no suggestions. See [BriefingSource.planLean]. */
    override suspend fun planLean(site: Site, date: LocalDate, kit: GearKit): NightReport {
        val fr = fetchForecast(site, forceRefresh = false)
        val failure = fr.exceptionOrNull()?.let { forecastUserText(it) }
        return NightPlanner.plan(
            site, date, fr.getOrNull()?.forecast, kit, fr.getOrNull()?.status,
            fr.getOrNull()?.warnings.orEmpty() + listOfNotNull(failure),
            includeSuggestions = false,
        )
    }

    private fun forecastUserText(error: Throwable): String =
        (error as? WeatherApiException)?.userText() ?: "Couldn't load the forecast"

    private suspend fun fetchForecast(site: Site, forceRefresh: Boolean): Result<ForecastResult> =
        try {
            Result.success(forecasts.forecast(site.latitude, site.longitude, forceRefresh))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun skyEventsFor(start: LocalDate, days: Int, zone: ZoneId): List<SkyEvent> {
        val end = start.plusDays((days.coerceAtLeast(1) - 1).toLong())
        return try {
            SkyEvents.between(start, end, zone)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchKp(): KpForecast? {
        val source = kp ?: return null
        return try {
            source.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchComets(): CometElementsFile? {
        val source = comets ?: return null
        return try {
            source.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchIss(): IssTle? {
        val source = iss ?: return null
        return try {
            source.fetchIss()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchAirQuality(site: Site): AirQualitySeries? {
        val source = airQuality ?: return null
        return try {
            source.fetch(site.latitude, site.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Air quality is per site, so each fetched site gets its own request. One failure only drops that site. */
    private suspend fun fetchAirQualityForSites(sites: List<Site>): Map<String, AirQualitySeries> {
        val source = airQuality ?: return emptyMap()
        return coroutineScope {
            sites.map { site ->
                async {
                    val series = try {
                        source.fetch(site.latitude, site.longitude)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    site.id to series
                }
            }.awaitAll().mapNotNull { (id, series) -> series?.let { id to it } }.toMap()
        }
    }

    private fun NightReport.withAurora(forecast: KpForecast?): NightReport {
        if (forecast == null) return this
        return copy(aurora = Aurora.forNight(site, ephemeris, forecast))
    }

    private fun NightReport.withIss(tle: IssTle?): NightReport {
        if (tle == null) return this
        val window = ephemeris.darkWindow ?: return this
        val passes = runCatching {
            IssPasses.during(
                tle.line1,
                tle.line2,
                window.start,
                window.end,
                site.latitude,
                site.longitude,
            )
        }.getOrDefault(emptyList())
        return copy(issPasses = passes)
    }

    private fun NightReport.withSmoke(series: AirQualitySeries?): NightReport {
        if (series == null) return this
        val dark = ephemeris.darkWindow ?: return this
        val inside = timeline.map { it.time }.filter { it in dark }
        val darkHours = if (inside.isNotEmpty()) inside else listOf(dark.start.plus(dark.duration.dividedBy(2)))
        val smoke = Smoke.assess(darkHours, series) ?: return this
        return copy(smoke = smoke)
    }

    private fun NightReport.withComet(orbits: List<CometOrbit>): NightReport {
        if (orbits.isEmpty()) return this
        val outlook = try {
            CometAdvisor.forNight(ephemeris, orbits)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return this
        } ?: return this
        return copy(comet = outlook)
    }

    /** Orbits from one fetched file. A parse failure drops the comet card and keeps the briefing. */
    private fun parseComets(file: CometElementsFile?): List<CometOrbit> {
        if (file == null) return emptyList()
        return try {
            cometParser(file.text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
