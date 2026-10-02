package app.nightbrief.data

import app.nightbrief.gear.GearCatalog
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsRecoveryTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val cottage = Site("cottage", "Cottage", 45.0, -78.5, "America/Toronto", bortle = 3)

    @Test
    fun distinctSitesNeverShareANotificationSlot() {
        val ids = (0..400).map { "site-$it" }
        val oldBuckets = ids.groupBy { it.hashCode() and 0x0FFF }
        assertTrue(oldBuckets.any { it.value.size > 1 })
        var book = SiteBook()
        ids.forEach { id -> book = book.add(Site(id, id, 0.0, 0.0, "UTC")) }
        val state = AppState(onboardingComplete = true, sites = book).ensureNotificationSlots()
        assertEquals(ids.size, state.notificationSlots.values.toSet().size)
        assertTrue(state.notificationSlots.values.all { it > 0 })
    }

    @Test
    fun deletedSiteDoesNotGiveItsSlotToTheNextOne() {
        val saved = AppState(sites = SiteBook().add(home).add(cottage)).ensureNotificationSlots()
        val homeSlot = saved.notificationSlots.getValue(home.id)
        val withoutHome = saved.copy(sites = saved.sites.delete(home.id)).ensureNotificationSlots()
        val again = withoutHome.copy(sites = withoutHome.sites.add(home)).ensureNotificationSlots()
        assertNotEquals(homeSlot, again.notificationSlots.getValue(home.id))
        assertEquals(saved.notificationSlots.getValue(cottage.id), again.notificationSlots.getValue(cottage.id))
    }

    @Test
    fun libraryRoundTripKeepsSitesAndGearOnly() {
        val state = AppState(
            onboardingComplete = true,
            sites = SiteBook().add(home, makePrimary = true).add(cottage),
            gear = GearCatalog.exampleKit,
            digestTime = "06:30",
            notificationSlots = mapOf(home.id to 4),
            notificationSlotNext = 5,
        )
        val decoded = LibraryTransfer.decode(LibraryTransfer.encode(state))
        assertEquals(state.sites, decoded.sites)
        assertEquals(state.gear, decoded.gear)
        val imported = LibraryTransfer.apply(
            AppState(digestTime = "09:00", notificationSlotNext = 8),
            decoded,
        )
        assertEquals("09:00", imported.digestTime)
        assertEquals(setOf(home.id, cottage.id), imported.notificationSlots.keys)
        assertTrue(imported.notificationSlots.values.all { it >= 8 })
    }

    @Test(expected = LibraryFormatException::class)
    fun emptyObjectIsNotALibrary() {
        LibraryTransfer.decode("{}")
    }

    @Test(expected = LibraryFormatException::class)
    fun corruptLibraryIsRejected() {
        LibraryTransfer.decode("not json")
    }

    @Test(timeout = 30_000)
    fun corruptSettingsFileRestoresLastKnownGood() = runBlocking {
        val dir = File.createTempFile("nightbrief-settings", "").apply {
            delete()
            mkdirs()
        }
        val file = File(dir, "app_state.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val restoredScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repo = SettingsRepository.create(file, scope)
            val saved = repo.update {
                AppState(
                    onboardingComplete = true,
                    sites = SiteBook().add(home, makePrimary = true),
                    gear = GearCatalog.exampleKit,
                    digestTime = "07:15",
                )
            }
            val backup = SettingsRepository.backupFileFor(file)
            assertTrue(backup.isFile)
            assertEquals(saved.digestTime, SettingsRepository.readBackup(backup)?.digestTime)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.writeText("this is not settings")

            val restored = SettingsRepository.create(file, restoredScope).current()
            assertEquals(true, restored.onboardingComplete)
            assertEquals("home", restored.sites.primaryId)
            assertEquals("07:15", restored.digestTime)
            assertEquals(GearCatalog.exampleKit, restored.gear)
        } finally {
            scope.coroutineContext[Job]?.cancel()
            restoredScope.coroutineContext[Job]?.cancel()
            dir.deleteRecursively()
        }
    }
}
