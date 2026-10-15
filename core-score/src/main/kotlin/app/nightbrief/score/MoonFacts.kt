package app.nightbrief.score

import java.time.LocalDate

/**
 * Short Moon notes for the hero's long-press.
 * [forDate] is stable for a civil date: the index is the epoch day modulo [facts].
 * Reordering [facts] changes which night shows which note.
 */
object MoonFacts {
    val facts: List<String> = listOf(
        "The Moon is drifting away from Earth by about 3.8 cm a year.",
        "Moonlight is sunlight bouncing off rock, which is why a full moon can cast a shadow.",
        "The same face of the Moon points at Earth. The far side still gets two weeks of daylight.",
        "The terminator is where lunar day meets night, and craters there throw the longest shadows.",
        "A new moon rises and sets with the Sun, so the night stays moonless.",
        "Earthshine can light the dark part of a crescent: sunlight reflected from Earth onto the Moon.",
        "From new moon to new moon is about 29.5 days.",
        "A full moon rises near sunset and sets near sunrise, opposite the Sun.",
        "The Moon's surface gravity is about one sixth of Earth's.",
        "With no wind or rain, a footprint on the Moon can last for millions of years.",
    )

    init {
        require(facts.size >= 2) { "need at least two moon facts" }
    }

    fun forDate(date: LocalDate): String {
        val index = Math.floorMod(date.toEpochDay(), facts.size.toLong()).toInt()
        return facts[index]
    }
}
