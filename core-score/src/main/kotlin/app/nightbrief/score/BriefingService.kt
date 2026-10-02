package app.nightbrief.score

import app.nightbrief.astro.Darkness
import app.nightbrief.astro.IssPasses
import app.nightbrief.gear.GearKit
import app.nightbrief.sites.Site
import app.nightbrief.weather.ForecastResult
import app.nightbrief.weather.ForecastSource
import app.nightbrief.weather.ForecastRepository
import app.nightbrief.weather.IssTle
import app.nightbrief.weather.KpForecast
import app.nightbrief.weather.KpSource
import app.nightbrief.weather.TleSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
) {
    val band: Band? get() = score?.let(Band::of)
}

data class WeeklyOutlook(val site: Site, val nights: List<OutlookNight>) {
    val best: OutlookNight? get() = nights.filter { it.score != null }.maxByOrNull { it.score!! }

    fun headline(locale: Locale = Locale.getDefault()): String {
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

    suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport
}

class BriefingService(
    private val forecasts: ForecastSource,
    private val clock: Clock = Clock.systemUTC(),
    /** Planetary Kp. Null leaves [NightReport.aurora] empty; a failed fetch does the same and does not fail the briefing. */
    private val kp: KpSource? = null,
    /** ISS elements. Null or a failed fetch leaves [NightReport.issPasses] empty and does not fail the briefing. */
    private val iss: TleSource? = null,
) : BriefingSource {
    override suspend fun brief(
        sites: List<Site>,
        kit: GearKit,
        outlookDays: Int,
        forceRefresh: Boolean,
    ): Briefing = coroutineScope {
        val now = clock.instant()
        val kpDeferred = async { fetchKp() }
        val issDeferred = async { fetchIss() }
        // Sites in the same ~1 km cache cell share one fetch; ephemeris and scoring still run per site.
        val keys = sites.map { ForecastRepository.cacheKey(it.latitude, it.longitude) }
        val fetchedByKey = keys.distinct().map { key ->
            async {
                val site = sites[keys.indexOf(key)]
                key to runCatching { forecasts.forecast(site.latitude, site.longitude, forceRefresh) }
            }
        }.awaitAll().toMap()
        val fetched = sites.map { site ->
            site to fetchedByKey.getValue(ForecastRepository.cacheKey(site.latitude, site.longitude))
        }
        val kpForecast = kpDeferred.await()
        val issTle = issDeferred.await()

        val tonight = mutableListOf<NightReport>()
        val outlooks = mutableListOf<WeeklyOutlook>()
        for ((site, result) in fetched) {
            val fr: ForecastResult? = result.getOrNull()
            val warnings = buildList {
                fr?.warnings?.let(::addAll)
                result.exceptionOrNull()?.let { add("Forecast unavailable: ${it.message}") }
            }
            val start = NightPlanner.tonight(now, site.zone)
            val nights = (0 until outlookDays).map { offset ->
                NightPlanner.plan(
                    site = site,
                    date = start.plusDays(offset.toLong()),
                    forecast = fr?.forecast,
                    kit = kit,
                    forecastStatus = fr?.status,
                    warnings = warnings,
                    includeSuggestions = offset == 0,
                ).withAurora(kpForecast).withIss(issTle)
            }
            tonight += nights.first()
            outlooks += WeeklyOutlook(
                site,
                nights.map {
                    OutlookNight(
                        it.date,
                        it.scoreValue,
                        it.ephemeris.moonIllumination,
                        it.ephemeris.darkness,
                        it.coverage,
                        estimated = it.score?.factors?.any { factor -> factor.estimated } == true,
                    )
                },
            )
        }
        Briefing(now, tonight, outlooks, kpForecast)
    }

    /** Plans a single site for a specific date (planning mode). */
    override suspend fun plan(site: Site, date: LocalDate, kit: GearKit): NightReport = coroutineScope {
        val kpDeferred = async { fetchKp() }
        val issDeferred = async { fetchIss() }
        val forecastDeferred = async { runCatching { forecasts.forecast(site.latitude, site.longitude) } }
        val kpForecast = kpDeferred.await()
        val issTle = issDeferred.await()
        val fr = forecastDeferred.await()
        NightPlanner.plan(
            site, date, fr.getOrNull()?.forecast, kit, fr.getOrNull()?.status,
            fr.getOrNull()?.warnings.orEmpty() + listOfNotNull(fr.exceptionOrNull()?.message),
        ).withAurora(kpForecast).withIss(issTle)
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
}
