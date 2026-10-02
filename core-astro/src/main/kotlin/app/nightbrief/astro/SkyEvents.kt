package app.nightbrief.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Year-ahead sky events: oppositions, elongations, conjunctions, eclipses, seasons, showers. */
enum class SkyEventKind {
    OPPOSITION,
    ELONGATION,
    CONJUNCTION,
    APPULSE,
    LUNAR_ECLIPSE,
    SOLAR_ECLIPSE,
    SEASON,
    SHOWER_PEAK,
}

data class SkyEvent(
    /** Refined to about the hour; the calendar shows its date in the site zone. */
    val instant: Instant,
    val kind: SkyEventKind,
    val title: String,
    val detail: String,
)

/**
 * A year-ahead planning view beside the 8-day forecast. Everything is computed on device
 * from [Bodies], [Planets], and [MeteorShowers]: no network, no database.
 *
 * Positions are planning-grade (see [Planets]): dates land within a day or two of published
 * values. Solar-eclipse types are a node-distance guess and visibility is not computed —
 * the detail line says so. Moon–planet appulses mix the date-frame Moon with J2000 planets
 * (~0.3°), well inside the 3° threshold.
 */
object SkyEvents {
    /** Planet–planet close approaches closer than this are listed. */
    const val CONJUNCTION_LIMIT_DEG = 3.0

    /** Moon–planet appulses closer than this are listed. */
    const val APPULSE_LIMIT_DEG = 3.0

    /** Oppositions need an elongation peak above this. */
    const val OPPOSITION_MIN_ELONGATION = 170.0

    /** Days scanned ahead. */
    const val DAYS_AHEAD = 365

    fun yearAhead(from: LocalDate, zone: ZoneId): List<SkyEvent> {
        val days = (0..DAYS_AHEAD).map { from.plusDays(it.toLong()) }
        val noons = days.associateWith { it.atTime(12, 0).atZone(zone).toInstant() }
        val events = mutableListOf<SkyEvent>()
        events += seasons(days, noons)
        events += showerPeaks(days, noons)
        events += oppositionsAndElongations(days, noons)
        events += conjunctions(days, noons)
        events += appulses(days, noons)
        events += eclipses(days, noons)
        return events.sortedBy { it.instant }
    }

    // -- Seasons (Sun ecliptic-longitude crossings) --

    private fun seasons(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val targets = listOf(
            Triple(0.0, "March equinox", "Spring begins in the north · days grow longer"),
            Triple(90.0, "June solstice", "Summer begins in the north · longest day"),
            Triple(180.0, "September equinox", "Autumn begins in the north · nights grow longer"),
            Triple(270.0, "December solstice", "Winter begins in the north · longest night"),
        )
        return targets.mapNotNull { (target, title, detail) ->
            crossing(days, noons, target) { sunLambda(it) }?.let { SkyEvent(it, SkyEventKind.SEASON, title, detail) }
        }
    }

    private fun sunLambda(instant: Instant): Double =
        Bodies.sunEclipticLongitude(AstroTime.daysSinceJ2000(instant))

    /** First instant where f crosses [target] upward (mod 360), refined by bisection. */
    private fun crossing(
        days: List<LocalDate>,
        noons: Map<LocalDate, Instant>,
        target: Double,
        f: (Instant) -> Double,
    ): Instant? {
        for (i in 0 until days.size - 1) {
            val a = norm360(f(noons.getValue(days[i])) - target)
            val b = norm360(f(noons.getValue(days[i + 1])) - target)
            // Upward crossing: residue wraps from just below 360 to just above 0.
            if (a > 350.0 && b < 10.0 && b - a + 360.0 < 30.0) {
                var lo = noons.getValue(days[i]).epochSecond.toDouble()
                var hi = noons.getValue(days[i + 1]).epochSecond.toDouble()
                repeat(24) {
                    val mid = (lo + hi) / 2
                    val r = norm360(f(Instant.ofEpochSecond(mid.toLong())) - target)
                    if (r > 180.0) lo = mid else hi = mid
                }
                return Instant.ofEpochSecond(((lo + hi) / 2).toLong())
            }
        }
        return null
    }

    // -- Meteor shower peaks --

    private fun showerPeaks(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val inRange = days.toSet()
        val years = listOf(days.first().year, days.first().year + 1)
        return MeteorShowers.annual.mapNotNull { shower ->
            val date = years
                .map { y -> LocalDate.of(y, shower.peakMonth, shower.peakDay) }
                .firstOrNull { it in inRange } ?: return@mapNotNull null
            val illumination = Bodies.moonIllumination(AstroTime.daysSinceJ2000(noons.getValue(date))).first
            val moon = "${(illumination * 100).roundToInt()}% moon"
            SkyEvent(
                noons.getValue(date),
                SkyEventKind.SHOWER_PEAK,
                "${shower.name} peak",
                "ZHR ~${shower.peakZhr} · under a $moon",
            )
        }
    }

    // -- Oppositions and greatest elongations --

    private fun oppositionsAndElongations(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val events = mutableListOf<SkyEvent>()
        for (planet in Planet.entries) {
            val elong = days.associateWith { Planets.position(planet, noons.getValue(it)).elongationDeg }
            for (i in 1 until days.size - 1) {
                val prev = elong.getValue(days[i - 1])
                val at = elong.getValue(days[i])
                val next = elong.getValue(days[i + 1])
                if (at < prev || at < next) continue
                val outer = planet != Planet.MERCURY && planet != Planet.VENUS
                val floor = if (outer) OPPOSITION_MIN_ELONGATION else if (planet == Planet.VENUS) 30.0 else 10.0
                if (at < floor) continue
                val peak = refine(days[i], noons) { Planets.position(planet, it).elongationDeg }
                val pos = Planets.position(planet, peak)
                if (outer) {
                    events += SkyEvent(
                        peak, SkyEventKind.OPPOSITION, "${planet.label} at opposition",
                        "Closest all year · mag ${"%.1f".format(pos.magnitude)} · up all night",
                    )
                } else {
                    val side = if (pos.eastOfSun) "evening" else "morning"
                    events += SkyEvent(
                        peak, SkyEventKind.ELONGATION,
                        "${planet.label} at greatest ${if (pos.eastOfSun) "eastern" else "western"} elongation",
                        "${"%.0f".format(pos.elongationDeg)}° from the Sun · $side · mag ${"%.1f".format(pos.magnitude)}",
                    )
                }
            }
        }
        return events
    }

    // -- Planet–planet conjunctions --

    private fun conjunctions(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val events = mutableListOf<SkyEvent>()
        val planets = Planet.entries
        // Planet positions once per day; pairs read from the same table.
        val table = days.associateWith { Planets.all(noons.getValue(it)) }
        for (a in planets.indices) {
            for (b in a + 1 until planets.size) {
                val pa = planets[a]
                val pb = planets[b]
                val sep = days.associateWith { day ->
                    val ra = table.getValue(day).getValue(pa).raDec
                    val rb = table.getValue(day).getValue(pb).raDec
                    Planets.separationDeg(ra.raDeg, ra.decDeg, rb.raDeg, rb.decDeg)
                }
                for (i in 1 until days.size - 1) {
                    val at = sep.getValue(days[i])
                    if (at >= sep.getValue(days[i - 1]) || at > sep.getValue(days[i + 1])) continue
                    if (at >= CONJUNCTION_LIMIT_DEG) continue
                    val lo = noons.getValue(days[i - 1])
                    val hi = noons.getValue(days[i + 1])
                    val best = golden(lo, hi) { t ->
                        val xa = Planets.position(pa, t).raDec
                        val xb = Planets.position(pb, t).raDec
                        Planets.separationDeg(xa.raDeg, xa.decDeg, xb.raDeg, xb.decDeg)
                    }
                    val closest = separationAt(pa, pb, best)
                    if (closest >= CONJUNCTION_LIMIT_DEG) continue
                    // Both planets must clear the Sun's glare to be worth listing.
                    val ea = Planets.position(pa, best).elongationDeg
                    val eb = Planets.position(pb, best).elongationDeg
                    if (ea < 12.0 || eb < 12.0) continue
                    val ma = Planets.position(pa, best).magnitude
                    val mb = Planets.position(pb, best).magnitude
                    val (first, second, mf, ms) = if (ma <= mb) Quad(pa, pb, ma, mb) else Quad(pb, pa, mb, ma)
                    events += SkyEvent(
                        best, SkyEventKind.CONJUNCTION,
                        "${first.label} passes ${"%.1f".format(closest)}° from ${second.label}",
                        "${first.label} (mag ${"%.1f".format(mf)}) and ${second.label} (mag ${"%.1f".format(ms)})",
                    )
                }
            }
        }
        return events
    }

    private data class Quad(val first: Planet, val second: Planet, val mf: Double, val ms: Double)

    private fun separationAt(a: Planet, b: Planet, t: Instant): Double {
        val xa = Planets.position(a, t).raDec
        val xb = Planets.position(b, t).raDec
        return Planets.separationDeg(xa.raDeg, xa.decDeg, xb.raDeg, xb.decDeg)
    }

    // -- Moon–planet appulses --

    private fun appulses(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val targets = listOf(Planet.VENUS, Planet.MARS, Planet.JUPITER, Planet.SATURN)
        // The Moon moves ~13°/day, so daily samples would skip close appulses: step 6 hours.
        val steps = (0..DAYS_AHEAD * 4).map { noons.getValue(days.first()).plusSeconds(it * 6 * 3600L) }
        val moons = steps.associateWith { Bodies.moonEquatorial(AstroTime.daysSinceJ2000(it)).first }
        val events = mutableListOf<SkyEvent>()
        for (planet in targets) {
            val sep = steps.associateWith { t ->
                val m = moons.getValue(t)
                val p = Planets.position(planet, t).raDec
                Planets.separationDeg(m.raDeg, m.decDeg, p.raDeg, p.decDeg)
            }
            for (i in 1 until steps.size - 1) {
                val at = sep.getValue(steps[i])
                if (at >= sep.getValue(steps[i - 1]) || at > sep.getValue(steps[i + 1])) continue
                if (at >= APPULSE_LIMIT_DEG) continue
                val best = golden(steps[i - 1], steps[i + 1]) { t ->
                    val m = Bodies.moonEquatorial(AstroTime.daysSinceJ2000(t)).first
                    val p = Planets.position(planet, t).raDec
                    Planets.separationDeg(m.raDeg, m.decDeg, p.raDeg, p.decDeg)
                }
                val closest = separationMoonPlanet(planet, best)
                if (closest >= APPULSE_LIMIT_DEG) continue
                val lit = (Bodies.moonIllumination(AstroTime.daysSinceJ2000(best)).first * 100).roundToInt()
                events += SkyEvent(
                    best, SkyEventKind.APPULSE,
                    "Moon passes ${"%.1f".format(closest)}° from ${planet.label}",
                    "Moon $lit% lit",
                )
            }
        }
        return events
    }

    private fun separationMoonPlanet(planet: Planet, t: Instant): Double {
        val m = Bodies.moonEquatorial(AstroTime.daysSinceJ2000(t)).first
        val p = Planets.position(planet, t).raDec
        return Planets.separationDeg(m.raDeg, m.decDeg, p.raDeg, p.decDeg)
    }

    // -- Eclipses --

    private fun eclipses(days: List<LocalDate>, noons: Map<LocalDate, Instant>): List<SkyEvent> {
        val events = mutableListOf<SkyEvent>()
        val illum = days.associateWith { Bodies.moonIllumination(AstroTime.daysSinceJ2000(noons.getValue(it))).first }
        for (i in 1 until days.size - 1) {
            val at = illum.getValue(days[i])
            val isFull = at >= illum.getValue(days[i - 1]) && at > illum.getValue(days[i + 1]) && at > 0.9
            val isNew = at <= illum.getValue(days[i - 1]) && at < illum.getValue(days[i + 1]) && at < 0.1
            if (!isFull && !isNew) continue
            val best = if (isFull) {
                goldenMax(noons.getValue(days[i - 1]), noons.getValue(days[i + 1])) {
                    Bodies.moonIllumination(AstroTime.daysSinceJ2000(it)).first
                }
            } else {
                golden(noons.getValue(days[i - 1]), noons.getValue(days[i + 1])) {
                    Bodies.moonIllumination(AstroTime.daysSinceJ2000(it)).first
                }
            }
            val beta = Bodies.moonEcliptic(AstroTime.daysSinceJ2000(best)).betaDeg
            if (isFull && kotlin.math.abs(beta) < 1.30) {
                val title = when {
                    kotlin.math.abs(beta) < 0.4 -> "Total lunar eclipse"
                    kotlin.math.abs(beta) < 0.75 -> "Partial lunar eclipse"
                    else -> "Penumbral lunar eclipse"
                }
                events += SkyEvent(
                    best, SkyEventKind.LUNAR_ECLIPSE, title,
                    "The Moon crosses Earth's shadow · visible wherever the Moon is up",
                )
            }
            // Distance-aware limits: the false friend (a high new Moon at apogee) sits
            // outside moonSemi + sunSemi + parallax while real partials sit inside it.
            // A grazing central line can still read Partial; the map note covers that.
            if (isNew) {
                val parallax = Bodies.moonEcliptic(AstroTime.daysSinceJ2000(best)).parallaxDeg
                val moonKm = 6378.14 / kotlin.math.sin(parallax * DEG)
                val sunKm = 149597870.7 * earthSunAu(best)
                val limit = asinD(1737.4 / moonKm) + asinD(696340.0 / sunKm) + parallax
                if (kotlin.math.abs(beta) < limit) {
                    val title = if (kotlin.math.abs(beta) < parallax) {
                        if (moonCoversSun(best)) "Total solar eclipse" else "Annular solar eclipse"
                    } else {
                        "Partial solar eclipse"
                    }
                    events += SkyEvent(
                        best, SkyEventKind.SOLAR_ECLIPSE, title,
                        "Visible somewhere on Earth — check a visibility map for your site",
                    )
                }
            }
        }
        return events
    }

    /** True when the Moon's apparent disc covers the Sun's (total vs annular). */
    internal fun moonCoversSun(t: Instant): Boolean {
        val parallax = Bodies.moonEcliptic(AstroTime.daysSinceJ2000(t)).parallaxDeg
        // Parallax asin(R_earth / d_moon): recover the Moon's distance in km.
        val moonKm = 6378.14 / kotlin.math.sin(parallax * DEG)
        val sunKm = 149597870.7 * earthSunAu(t)
        return (3474.2 / moonKm) >= (1392700.0 / sunKm)
    }

    private fun earthSunAu(t: Instant): Double {
        val d = AstroTime.daysSinceJ2000(t)
        val g = norm360(357.528 + 0.9856003 * d)
        return 1.00014 - 0.01671 * cosD(g) - 0.00014 * cosD(2 * g)
    }

    // -- Numeric helpers --

    /** Refines a daily local maximum at [day] over ±[spanDays] by golden-section search. */
    private fun refine(day: LocalDate, noons: Map<LocalDate, Instant>, spanDays: Long = 2, f: (Instant) -> Double): Instant {
        val centre = noons.getValue(day)
        return goldenMax(centre.minusSeconds(spanDays * 86400), centre.plusSeconds(spanDays * 86400), f)
    }

    private fun goldenMax(lo: Instant, hi: Instant, f: (Instant) -> Double): Instant =
        golden(lo, hi, negate(f))

    private fun negate(f: (Instant) -> Double): (Instant) -> Double = { -f(it) }

    /** Golden-section minimiser over instants, good to about a minute after 40 rounds. */
    internal fun golden(lo: Instant, hi: Instant, f: (Instant) -> Double): Instant {
        var a = lo.epochSecond.toDouble()
        var b = hi.epochSecond.toDouble()
        val invPhi = (kotlin.math.sqrt(5.0) - 1) / 2
        var c = b - invPhi * (b - a)
        var d = a + invPhi * (b - a)
        var fc = f(Instant.ofEpochSecond(c.toLong()))
        var fd = f(Instant.ofEpochSecond(d.toLong()))
        repeat(40) {
            if (fc < fd) {
                b = d; d = c; fd = fc
                c = b - invPhi * (b - a)
                fc = f(Instant.ofEpochSecond(c.toLong()))
            } else {
                a = c; c = d; fc = fd
                d = a + invPhi * (b - a)
                fd = f(Instant.ofEpochSecond(d.toLong()))
            }
        }
        return Instant.ofEpochSecond(((a + b) / 2).toLong())
    }
}
