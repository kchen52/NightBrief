package app.nightbrief.gear

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearNicknameTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun olderGearJsonWithoutNicknameStillDecodes() {
        val raw = """
            {"bodies":[{"id":"canon-r7","name":"Canon EOS R7","sensorWidthMm":22.3,"sensorHeightMm":14.8,"megapixels":32.5}],
             "lenses":[{"id":"sigma","name":"Sigma 10mm","minFocalMm":10.0,"maxFocalMm":10.0,"maxAperture":2.8}],
             "primaryBodyId":"canon-r7"}
        """.trimIndent()
        val kit = json.decodeFromString(GearKit.serializer(), raw)
        assertEquals("Canon EOS R7", kit.primaryBody?.name)
        assertNull(kit.primaryBody?.nickname)
        assertNull(kit.lenses.single().nickname)
    }

    @Test
    fun nicknameRoundTripsThroughGearJson() {
        val kit = GearCatalog.exampleKit.copy(
            bodies = listOf(GearCatalog.canonR7.copy(nickname = "Old Faithful")),
            lenses = listOf(GearCatalog.sigma10to18.copy(nickname = "the 10mm")),
        )
        val saved = json.decodeFromString(
            GearKit.serializer(),
            json.encodeToString(GearKit.serializer(), kit),
        )
        assertEquals("Old Faithful", saved.primaryBody?.nickname)
        assertEquals("the 10mm", saved.lenses.single().nickname)
    }

    @Test
    fun unsetNicknameKeepsTheTechnicalExposureLine() {
        val suggestion = suggestion()
        assertEquals("10mm · f/2.8 · 20s · ISO 3200", suggestion.summary)
        val blank = suggestion(
            body = GearCatalog.canonR7.copy(nickname = "   "),
            lens = GearCatalog.sigma10to18.copy(nickname = ""),
        )
        assertEquals("10mm · f/2.8 · 20s · ISO 3200", blank.summary)
    }

    @Test
    fun bodyNicknameNamesTheExposureLine() {
        val suggestion = suggestion(body = GearCatalog.canonR7.copy(nickname = "Old Faithful"))
        assertEquals("Old Faithful + the 10mm: 20 s at ISO 3200.", suggestion.summary)
    }

    @Test
    fun lensNicknameReplacesTheFocalPhrase() {
        val suggestion = suggestion(
            body = GearCatalog.canonR7.copy(nickname = "Old Faithful"),
            lens = GearCatalog.sigma10to18.copy(nickname = "Little Sigma"),
        )
        assertEquals("Old Faithful + Little Sigma: 20 s at ISO 3200.", suggestion.summary)
    }

    @Test
    fun lensNicknameAloneStillNamesTheLine() {
        val suggestion = suggestion(lens = GearCatalog.sigma10to18.copy(nickname = "the 10mm"))
        assertEquals("the 10mm: 20 s at ISO 3200.", suggestion.summary)
    }

    @Test
    fun trackedNicknameKeepsTheStackPlan() {
        val suggestion = suggestion(body = GearCatalog.canonR7.copy(nickname = "Old Faithful")).copy(
            tracked = true,
            stackCount = 90,
            integrationMinutes = 30,
        )
        assertEquals(
            "Old Faithful + the 10mm: 20 s at ISO 3200. · 90×20s (30 min)",
            suggestion.summary,
        )
    }

    private fun suggestion(
        body: CameraBody = GearCatalog.canonR7,
        lens: Lens = GearCatalog.sigma10to18,
    ) = ExposureSuggestion(
        body = body,
        lens = lens,
        focalMm = 10.0,
        aperture = 2.8,
        shutterSeconds = 20.0,
        npfSeconds = 22.0,
        rule500Seconds = 31.0,
        iso = 3200,
        reachesTarget = true,
    )
}
