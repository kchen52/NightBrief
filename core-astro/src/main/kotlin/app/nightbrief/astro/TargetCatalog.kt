package app.nightbrief.astro

enum class TargetKind(val label: String) {
    MILKY_WAY("Milky Way"),
    GALAXY("Galaxy"),
    EMISSION_NEBULA("Emission nebula"),
    REFLECTION_NEBULA("Reflection nebula"),
    STAR_CLUSTER("Star cluster"),
    CONSTELLATION("Constellation / asterism"),
}

/**
 * A photographic target at a fixed position.
 *
 * [maxBortle] and [maxMoonIllumination] are the worst conditions under which the target is
 * still worth shooting; [minFocalMm]..[idealFocalMm] is the useful focal-length range (full-frame equivalent).
 */
data class Target(
    val id: String,
    val name: String,
    val kind: TargetKind,
    val position: RaDec,
    val maxBortle: Int,
    val maxMoonIllumination: Double,
    val minFocalMm: Int,
    val idealFocalMm: Int,
    val minAltitudeDeg: Double = 20.0,
    val tip: String,
)

object TargetCatalog {
    val milkyWayCore = Target(
        id = "mw-core",
        name = "Milky Way core",
        kind = TargetKind.MILKY_WAY,
        position = Ephemeris.GALACTIC_CENTER,
        maxBortle = 5,
        maxMoonIllumination = 0.35,
        minFocalMm = 12,
        idealFocalMm = 24,
        minAltitudeDeg = NightEphemeris.DEFAULT_MILKY_WAY_MIN_ALTITUDE,
        tip = "Frame a foreground to the south; stack 10–20 frames to cut noise.",
    )

    val all: List<Target> = listOf(
        milkyWayCore,
        Target(
            "cygnus", "Cygnus Milky Way (Summer Triangle)", TargetKind.MILKY_WAY,
            RaDec(305.0, 40.0), maxBortle = 5, maxMoonIllumination = 0.4,
            minFocalMm = 14, idealFocalMm = 35, minAltitudeDeg = 30.0,
            tip = "Rich northern Milky Way with the North America Nebula and dark rifts.",
        ),
        Target(
            "m31", "Andromeda Galaxy (M31)", TargetKind.GALAXY,
            RaDec(10.685, 41.269), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 50, idealFocalMm = 200, minAltitudeDeg = 30.0,
            tip = "Visible as a smudge at 24mm; 135–300mm on a tracker shows the dust lanes.",
        ),
        Target(
            "m42", "Orion Nebula (M42)", TargetKind.EMISSION_NEBULA,
            RaDec(83.822, -5.391), maxBortle = 8, maxMoonIllumination = 0.8,
            minFocalMm = 50, idealFocalMm = 300, minAltitudeDeg = 25.0,
            tip = "Bright enough for suburban skies; bracket exposures to keep the core from clipping.",
        ),
        Target(
            "m45", "Pleiades (M45)", TargetKind.REFLECTION_NEBULA,
            RaDec(56.75, 24.117), maxBortle = 7, maxMoonIllumination = 0.6,
            minFocalMm = 50, idealFocalMm = 200, minAltitudeDeg = 30.0,
            tip = "The cluster works anywhere; the blue reflection nebulosity needs dark skies.",
        ),
        Target(
            "ngc7000", "North America Nebula (NGC 7000)", TargetKind.EMISSION_NEBULA,
            RaDec(314.75, 44.333), maxBortle = 4, maxMoonIllumination = 0.3,
            minFocalMm = 35, idealFocalMm = 135, minAltitudeDeg = 30.0,
            tip = "Faint in visible light — long integration or an H-alpha/dual-band filter helps.",
        ),
        Target(
            "m8", "Lagoon & Trifid Nebulae (M8/M20)", TargetKind.EMISSION_NEBULA,
            RaDec(270.925, -24.38), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 50, idealFocalMm = 200, minAltitudeDeg = 15.0,
            tip = "Sits just above the galactic core; shoot when it's highest in the south.",
        ),
        Target(
            "double-cluster", "Double Cluster (NGC 869/884)", TargetKind.STAR_CLUSTER,
            RaDec(35.0, 57.133), maxBortle = 7, maxMoonIllumination = 0.7,
            minFocalMm = 85, idealFocalMm = 300, minAltitudeDeg = 30.0,
            tip = "Two bright clusters side by side; tolerant of moonlight.",
        ),
        Target(
            "m13", "Hercules Cluster (M13)", TargetKind.STAR_CLUSTER,
            RaDec(250.423, 36.461), maxBortle = 7, maxMoonIllumination = 0.7,
            minFocalMm = 200, idealFocalMm = 600, minAltitudeDeg = 35.0,
            tip = "A tight globular — needs reach, but punches through light pollution.",
        ),
        Target(
            "orion", "Orion (constellation)", TargetKind.CONSTELLATION,
            RaDec(83.0, 0.0), maxBortle = 8, maxMoonIllumination = 1.0,
            minFocalMm = 24, idealFocalMm = 50, minAltitudeDeg = 25.0,
            tip = "Great widefield with a diffusion filter to bloom the bright stars' colours.",
        ),
        Target(
            "big-dipper", "Big Dipper / Ursa Major", TargetKind.CONSTELLATION,
            RaDec(178.0, 55.0), maxBortle = 9, maxMoonIllumination = 1.0,
            minFocalMm = 14, idealFocalMm = 35, minAltitudeDeg = 25.0,
            tip = "Recognisable even from the city; pair it with a landmark.",
        ),
        Target(
            "polaris-trails", "Star trails around Polaris", TargetKind.CONSTELLATION,
            RaDec(37.95, 89.264), maxBortle = 9, maxMoonIllumination = 1.0,
            minFocalMm = 10, idealFocalMm = 24, minAltitudeDeg = 10.0,
            tip = "Stack 100+ frames for circular trails; moonlight lights the foreground nicely.",
        ),
    )
}
