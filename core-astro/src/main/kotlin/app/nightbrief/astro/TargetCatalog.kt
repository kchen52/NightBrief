package app.nightbrief.astro

import kotlinx.serialization.Serializable

@Serializable
enum class TargetKind(val label: String) {
    MILKY_WAY("Milky Way"),
    GALAXY("Galaxy"),
    EMISSION_NEBULA("Emission nebula"),
    REFLECTION_NEBULA("Reflection nebula"),
    STAR_CLUSTER("Star cluster"),
    CONSTELLATION("Constellation / asterism"),
    MOON("Moon"),
    MOONLIT("Moonlit landscape");

    companion object {
        /** Fixed-position kinds a user-added target may pick (never lunar). */
        val customKinds: List<TargetKind> = entries.filter { it != MOON && it != MOONLIT }
    }
}

/**
 * A photographic target at a fixed position.
 *
 * [maxBortle] and [maxMoonIllumination] are the worst conditions under which the target is
 * still worth shooting; [minFocalMm]..[idealFocalMm] is the useful focal-length range (full-frame equivalent).
 */
@Serializable
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
    /** True when a tracking mount would spoil the shot (star trails). Omitted from the plan while a tracker is in the kit. */
    val untrackedOnly: Boolean = false,
    /** When true, [position] is ignored and the Moon's coordinates are used. */
    val tracksMoon: Boolean = false,
    /** Offered only while the Moon is more than half illuminated. */
    val requiresBrightMoon: Boolean = false,
)

object TargetCatalog {
    /** Lunar subjects are offered only above this illuminated fraction. */
    const val BRIGHT_MOON_ILLUMINATION = 0.5

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
            untrackedOnly = true,
        ),
        Target(
            "ngc3372", "Carina Nebula (NGC 3372)", TargetKind.EMISSION_NEBULA,
            RaDec(161.285, -59.867), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 50, idealFocalMm = 200, minAltitudeDeg = 20.0,
            tip = "Southern showpiece emission complex; an H-alpha or dual-band filter makes the dust pillars pop.",
        ),
        Target(
            "lmc", "Large Magellanic Cloud (LMC)", TargetKind.GALAXY,
            RaDec(80.894, -69.756), maxBortle = 5, maxMoonIllumination = 0.4,
            minFocalMm = 35, idealFocalMm = 135, minAltitudeDeg = 20.0,
            tip = "Sprawling satellite galaxy with the Tarantula Nebula inside; frame wide at 50–135mm.",
        ),
        Target(
            "smc", "Small Magellanic Cloud (SMC)", TargetKind.GALAXY,
            RaDec(13.187, -72.828), maxBortle = 5, maxMoonIllumination = 0.4,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 20.0,
            tip = "Pair it with the bright globular 47 Tucanae right beside the cloud.",
        ),
        Target(
            "ngc5139", "Omega Centauri (NGC 5139)", TargetKind.STAR_CLUSTER,
            RaDec(201.697, -47.480), maxBortle = 7, maxMoonIllumination = 0.7,
            minFocalMm = 135, idealFocalMm = 400, minAltitudeDeg = 20.0,
            tip = "The sky's largest globular cluster — resolves into a swarm of stars at 200mm and up.",
        ),
        Target(
            "m81", "Bode's & Cigar Galaxies (M81/M82)", TargetKind.GALAXY,
            RaDec(148.888, 69.065), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 135, idealFocalMm = 300, minAltitudeDeg = 30.0,
            tip = "Two galaxies in one frame: Bode's spiral beside the edge-on Cigar.",
        ),
        Target(
            "m51", "Whirlpool Galaxy (M51)", TargetKind.GALAXY,
            RaDec(202.469, 47.195), maxBortle = 5, maxMoonIllumination = 0.45,
            minFocalMm = 135, idealFocalMm = 400, minAltitudeDeg = 30.0,
            tip = "A face-on spiral with a companion; 200mm and up separates the two cores.",
        ),
        Target(
            "leo-triplet", "Leo Triplet (M65/M66/NGC 3628)", TargetKind.GALAXY,
            RaDec(170.063, 12.992), maxBortle = 5, maxMoonIllumination = 0.45,
            minFocalMm = 135, idealFocalMm = 300, minAltitudeDeg = 25.0,
            tip = "Three galaxies in one field; NGC 3628 is the edge-on one with the dust lane.",
        ),
        Target(
            "m101", "Pinwheel Galaxy (M101)", TargetKind.GALAXY,
            RaDec(210.803, 54.349), maxBortle = 4, maxMoonIllumination = 0.35,
            minFocalMm = 85, idealFocalMm = 250, minAltitudeDeg = 30.0,
            tip = "Big and face-on, but the arms are faint — dark skies or a long stack.",
        ),
        Target(
            "m33", "Triangulum Galaxy (M33)", TargetKind.GALAXY,
            RaDec(23.462, 30.660), maxBortle = 4, maxMoonIllumination = 0.35,
            minFocalMm = 50, idealFocalMm = 200, minAltitudeDeg = 25.0,
            tip = "Larger than the Moon and easy to miss; it needs a dark sky more than a long lens.",
        ),
        Target(
            "m104", "Sombrero Galaxy (M104)", TargetKind.GALAXY,
            RaDec(189.998, -11.623), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 135, idealFocalMm = 400, minAltitudeDeg = 20.0,
            tip = "A bright bulge cut by a dust lane; it wants focal length more than a dark sky.",
        ),
        Target(
            "ngc5128", "Centaurus A (NGC 5128)", TargetKind.GALAXY,
            RaDec(201.365, -43.019), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 85, idealFocalMm = 300, minAltitudeDeg = 20.0,
            tip = "The dust lane across this elliptical is the shot; 200mm frames the whole galaxy.",
        ),
        Target(
            "ngc253", "Sculptor Galaxy (NGC 253)", TargetKind.GALAXY,
            RaDec(11.888, -25.288), maxBortle = 5, maxMoonIllumination = 0.45,
            minFocalMm = 85, idealFocalMm = 300, minAltitudeDeg = 20.0,
            tip = "A bright edge-on spiral; shoot it when it's highest, and keep the horizon dark.",
        ),
        Target(
            "ngc6992", "Eastern Veil Nebula (NGC 6992)", TargetKind.EMISSION_NEBULA,
            RaDec(314.075, 31.717), maxBortle = 4, maxMoonIllumination = 0.3,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 30.0,
            tip = "A thin supernova filament; an H-alpha or dual-band filter is what makes it show up.",
        ),
        Target(
            "ngc2237", "Rosette Nebula (NGC 2237)", TargetKind.EMISSION_NEBULA,
            RaDec(97.979, 4.942), maxBortle = 5, maxMoonIllumination = 0.45,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 25.0,
            tip = "A round emission shell around a bright cluster; 85–135mm frames the whole ring.",
        ),
        Target(
            "ic434", "Horsehead & Flame (IC 434 / NGC 2024)", TargetKind.EMISSION_NEBULA,
            RaDec(85.246, -2.458), maxBortle = 5, maxMoonIllumination = 0.4,
            minFocalMm = 135, idealFocalMm = 400, minAltitudeDeg = 25.0,
            tip = "The Flame is the bright one; the Horsehead needs dark skies or a narrowband filter.",
        ),
        Target(
            "ngc1499", "California Nebula (NGC 1499)", TargetKind.EMISSION_NEBULA,
            RaDec(60.825, 36.417), maxBortle = 4, maxMoonIllumination = 0.3,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 30.0,
            tip = "A long faint arc beside Menkib; a hydrogen filter pulls it out of a bright sky.",
        ),
        Target(
            "ic1805", "Heart & Soul Nebulae (IC 1805/IC 1848)", TargetKind.EMISSION_NEBULA,
            RaDec(40.488, 60.940), maxBortle = 4, maxMoonIllumination = 0.35,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 25.0,
            tip = "Two emission lobes in Cassiopeia; 85–135mm fits both, and a dual-band filter helps.",
        ),
        Target(
            "m16", "Eagle Nebula (M16)", TargetKind.EMISSION_NEBULA,
            RaDec(274.700, -13.817), maxBortle = 6, maxMoonIllumination = 0.5,
            minFocalMm = 100, idealFocalMm = 300, minAltitudeDeg = 20.0,
            tip = "The Pillars of Creation sit in a brighter shell; 200mm reaches them.",
        ),
        Target(
            "m27", "Dumbbell Nebula (M27)", TargetKind.EMISSION_NEBULA,
            RaDec(299.901, 22.721), maxBortle = 7, maxMoonIllumination = 0.65,
            minFocalMm = 135, idealFocalMm = 400, minAltitudeDeg = 30.0,
            tip = "A bright planetary that punches through moonlight; a longer lens shows the dim outer shell.",
        ),
        Target(
            "m44", "Beehive Cluster (M44)", TargetKind.STAR_CLUSTER,
            RaDec(130.100, 19.667), maxBortle = 8, maxMoonIllumination = 0.8,
            minFocalMm = 50, idealFocalMm = 135, minAltitudeDeg = 30.0,
            tip = "A loose, bright cluster; it still looks like a cluster under a bright Moon.",
        ),
        Target(
            "ngc104", "47 Tucanae (NGC 104)", TargetKind.STAR_CLUSTER,
            RaDec(6.023, -72.081), maxBortle = 7, maxMoonIllumination = 0.7,
            minFocalMm = 85, idealFocalMm = 300, minAltitudeDeg = 20.0,
            tip = "A blazing globular beside the Small Magellanic Cloud; the two fit in one wide frame.",
        ),
        Target(
            "ngc4755", "Jewel Box (NGC 4755)", TargetKind.STAR_CLUSTER,
            RaDec(193.400, -60.367), maxBortle = 7, maxMoonIllumination = 0.7,
            minFocalMm = 85, idealFocalMm = 300, minAltitudeDeg = 20.0,
            tip = "A compact coloured cluster next to the Southern Cross; moonlight barely hurts it.",
        ),
        Target(
            "rho-oph", "Rho Ophiuchi cloud", TargetKind.REFLECTION_NEBULA,
            RaDec(246.396, -23.447), maxBortle = 4, maxMoonIllumination = 0.35,
            minFocalMm = 14, idealFocalMm = 35, minAltitudeDeg = 15.0,
            tip = "Colourful dust around Antares; it wants a dark sky and a wide lens.",
        ),
        Target(
            "scorpius", "Scorpius (constellation)", TargetKind.CONSTELLATION,
            RaDec(247.352, -26.432), maxBortle = 8, maxMoonIllumination = 1.0,
            minFocalMm = 14, idealFocalMm = 35, minAltitudeDeg = 15.0,
            tip = "The head and claws clear a mid-northern horizon; the tail needs a southern site.",
        ),
        Target(
            "crux", "Southern Cross & Coalsack", TargetKind.CONSTELLATION,
            RaDec(187.250, -60.0), maxBortle = 8, maxMoonIllumination = 1.0,
            minFocalMm = 14, idealFocalMm = 35, minAltitudeDeg = 20.0,
            tip = "Frame the Cross with the Coalsack; the Jewel Box sits in the eastern arm.",
        ),
        Target(
            "leo", "Leo (constellation)", TargetKind.CONSTELLATION,
            RaDec(165.0, 15.0), maxBortle = 8, maxMoonIllumination = 1.0,
            minFocalMm = 24, idealFocalMm = 50, minAltitudeDeg = 25.0,
            tip = "The sickle and the triangle make a wide spring portrait; a diffusion filter blooms Regulus.",
        ),
        Target(
            id = "moon-closeup",
            name = "Moon close-up",
            kind = TargetKind.MOON,
            position = RaDec(0.0, 0.0),
            maxBortle = 9,
            maxMoonIllumination = 1.0,
            minFocalMm = 200,
            idealFocalMm = 400,
            minAltitudeDeg = 15.0,
            tip = "Frame the terminator. Craters there throw the longest shadows, and a few days off full shows more relief than the full disk.",
            tracksMoon = true,
            requiresBrightMoon = true,
        ),
        Target(
            id = "moonlit-landscape",
            name = "Moonlit landscape",
            kind = TargetKind.MOONLIT,
            position = RaDec(0.0, 0.0),
            maxBortle = 9,
            maxMoonIllumination = 1.0,
            minFocalMm = 14,
            idealFocalMm = 24,
            minAltitudeDeg = 10.0,
            tip = "Face away from the Moon so it lights the foreground, and keep the ground from blowing out.",
            tracksMoon = true,
            requiresBrightMoon = true,
        ),
    )

    /**
     * Appends user-defined [customTargets] to [all].
     *
     * Customs are fixed-position targets only: any entry with [Target.tracksMoon],
     * [Target.requiresBrightMoon], or a lunar [TargetKind] is stripped so [all]'s built-in
     * lunar rules stay authoritative and every custom maps through `TargetAdvisor.interestOf`.
     */
    fun withCustoms(customTargets: List<Target>): List<Target> {
        if (customTargets.isEmpty()) return all
        val fixed = customTargets.filter {
            !it.tracksMoon && !it.requiresBrightMoon && it.kind in TargetKind.customKinds
        }
        return if (fixed.isEmpty()) all else all + fixed
    }

    /** Sensible default moon ceiling when the user adds a custom target of [kind]. */
    fun defaultMaxMoonIllumination(kind: TargetKind): Double = when (kind) {
        TargetKind.MILKY_WAY -> 0.35
        TargetKind.EMISSION_NEBULA -> 0.5
        TargetKind.GALAXY -> 0.5
        TargetKind.REFLECTION_NEBULA -> 0.6
        TargetKind.STAR_CLUSTER -> 0.7
        TargetKind.CONSTELLATION -> 1.0
        TargetKind.MOON, TargetKind.MOONLIT -> 1.0
    }
}
