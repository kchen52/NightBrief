package app.nightbrief.weather

/** Joins a secondary model's cloud series onto the primary hours by epoch. Hours the second model does not cover keep a null second opinion. */
fun Forecast.withSecondary(secondary: Forecast): Forecast {
    if (secondary.hours.isEmpty()) return this
    val byEpoch = secondary.hours.associate { it.epochSecond to it.cloudCover }
    if (byEpoch.values.all { it == null }) return this
    return copy(
        hours = hours.map { h ->
            val other = byEpoch[h.epochSecond]
            if (other != null) h.copy(cloudCoverSecondary = other) else h
        },
    )
}
