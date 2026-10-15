package app.nightbrief.score

/**
 * Pose of the Tonight owl. One pose wins: aurora possible or likely, then frost,
 * then dew, then the verdict. A night with no score squints, because there is no verdict yet.
 */
enum class MascotMood {
    AWAKE_TELESCOPE,
    SQUINTING_AT_CLOUD,
    ASLEEP,
    UMBRELLA,
    SCARF,
    LOOKING_UP,
    ;

    /**
     * TalkBack label for this pose on [report].
     * [LOOKING_UP] names the aurora chance; the other poses name the verdict or the dew risk.
     */
    fun contentDescription(report: NightReport): String = when (this) {
        AWAKE_TELESCOPE -> "Owl awake with a telescope: a go night"
        SQUINTING_AT_CLOUD -> if (report.score == null) {
            "Owl squinting at a cloud: no score yet"
        } else {
            "Owl squinting at a cloud: a maybe night"
        }
        ASLEEP -> "Owl asleep under a blanket: a no-go night"
        UMBRELLA -> "Owl holding an umbrella: dew is likely"
        SCARF -> "Owl wearing a scarf: frost is likely"
        LOOKING_UP -> when (report.aurora?.chance) {
            AuroraChance.LIKELY -> "Owl looking up at the aurora: aurora is likely"
            else -> "Owl looking up at the aurora: aurora is possible"
        }
    }

    companion object {
        fun of(report: NightReport): MascotMood {
            val aurora = report.aurora?.chance
            if (aurora == AuroraChance.LIKELY || aurora == AuroraChance.POSSIBLE) return LOOKING_UP
            val dew = report.dew
            if (dew?.frost == true) return SCARF
            if (dew?.risk == true) return UMBRELLA
            return when (report.score?.verdict) {
                Verdict.GO -> AWAKE_TELESCOPE
                Verdict.MAYBE -> SQUINTING_AT_CLOUD
                Verdict.NO_GO -> ASLEEP
                null -> SQUINTING_AT_CLOUD
            }
        }
    }
}
