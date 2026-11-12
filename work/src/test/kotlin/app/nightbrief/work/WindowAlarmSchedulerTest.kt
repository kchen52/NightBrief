package app.nightbrief.work

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.nightbrief.astro.TimeWindow
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.gear.GearCatalog
import app.nightbrief.score.Briefing
import app.nightbrief.score.NightPlanner
import app.nightbrief.score.NightReport
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.Forecast
import app.nightbrief.weather.HourlyWeather
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WindowAlarmSchedulerTest {
    private val home = Site("home", "Home", 43.65, -79.38, "America/Toronto", bortle = 4)
    private val date = LocalDate.of(2024, 8, 10)
    private val kit = GearCatalog.exampleKit

    @Before
    fun setUp() {
        val context = context()
        File(context.filesDir, "datastore").mkdirs()
        initWorkManager(context)
        val app = context.applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications(context).cancelAll()
        WindowAlarmScheduler.reschedule(context, AppState())
        DigestScheduler.reschedule(context, AppState())
    }

    @After
    fun tearDown() {
        val context = context()
        WindowAlarmScheduler.reschedule(context, AppState())
        DigestScheduler.reschedule(context, AppState())
    }

    @Test
    fun toggleArmsAtBestWindowStartOnRequestCode1002BesideTheDigestAlarm() {
        val context = context()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val report = clearReport()
        val base = AppState(onboardingComplete = true, sites = SiteBook().add(home), digestEnabled = true)
        val armed = WindowAlarmScheduler.toggle(base, report)

        DigestScheduler.reschedule(context, armed)

        assertEquals(
            "expected both the digest alarm (1001) and the window alarm (1002), got ${alarms.scheduledAlarms.size}",
            2,
            alarms.scheduledAlarms.size,
        )
        val windowScheduled = windowScheduledAlarm(alarms)
        assertNotNull("expected the 1002 window alarm to be scheduled", windowScheduled)
        assertEquals(report.score!!.bestWindow!!.start.toEpochMilli(), windowScheduled!!.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, windowScheduled.getType())
        assertEquals(true, windowScheduled.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, windowScheduled.windowLengthMs)

        val cancelled = WindowAlarmScheduler.toggle(armed, report)
        DigestScheduler.reschedule(context, cancelled)
        assertNull("toggling off should cancel the 1002 alarm", windowScheduledAlarm(alarms))
        assertEquals("the 1001 digest alarm should stay armed", 1, alarms.scheduledAlarms.size)
    }

    @Test
    @Config(sdk = [30])
    fun windowAlarmBelowApi31UsesExactAlarmWithoutThePlayPermission() {
        val context = context()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val report = clearReport()
        val armed = WindowAlarmScheduler.toggle(
            AppState(onboardingComplete = true, sites = SiteBook().add(home), digestEnabled = false),
            report,
        )

        WindowAlarmScheduler.reschedule(context, armed)

        val windowScheduled = windowScheduledAlarm(alarms)
        assertNotNull(windowScheduled)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, windowScheduled!!.windowLengthMs)
    }

    @Test
    fun refreshedBriefingMovesOrCancelsTheScheduledWindowAlarm() {
        val context = context()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val initial = clearReport()
        val base = AppState(onboardingComplete = true, sites = SiteBook().add(home), digestEnabled = false)
        val armed = WindowAlarmScheduler.toggle(base, initial)
        WindowAlarmScheduler.reschedule(context, armed)

        val shiftedStart = initial.score!!.bestWindow!!.start.plusSeconds(2 * 3600)
        val shiftedEnd = initial.score!!.bestWindow!!.end.plusSeconds(2 * 3600)
        val movedReport = initial.copy(
            score = initial.score!!.copy(bestWindow = TimeWindow(shiftedStart, shiftedEnd), bestWindowScore = 92),
        )
        val movedState = WindowAlarmScheduler.syncWithBriefing(
            context,
            armed,
            Briefing(Instant.EPOCH, listOf(movedReport), emptyList()),
        )
        assertEquals(shiftedStart.toEpochMilli(), windowScheduledAlarm(alarms)!!.triggerAtMs)
        assertEquals(92, movedState.windowAlarm!!.windowScore)

        val overcast = initial.copy(score = initial.score!!.copy(score = 14, bestWindowScore = 18))
        val cancelledState = WindowAlarmScheduler.syncWithBriefing(
            context,
            movedState,
            Briefing(Instant.EPOCH, listOf(overcast), emptyList()),
        )
        assertNull(cancelledState.windowAlarm)
        assertNull("a collapsed night should cancel the scheduled alarm", windowScheduledAlarm(alarms))
    }

    @Test
    fun deletingTheSiteCancelsTheScheduledWindowAlarm() {
        val context = context()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val report = clearReport()
        val armed = WindowAlarmScheduler.toggle(
            AppState(onboardingComplete = true, sites = SiteBook().add(home), digestEnabled = false),
            report,
        )
        WindowAlarmScheduler.reschedule(context, armed)
        assertNotNull(windowScheduledAlarm(alarms))

        val deleted = armed.copy(sites = armed.sites.delete(home.id))
        WindowAlarmScheduler.reschedule(context, deleted)
        assertNull("deleting the site should cancel the 1002 alarm", windowScheduledAlarm(alarms))
    }

    @Test
    fun firingPostsTheWindowNotificationWithExtraSiteIdAndClearsTheOneShotState() = runBlocking {
        val context = context()
        val graph = AppGraph.get(context)
        val report = clearReport()
        val armed = WindowAlarmScheduler.toggle(
            AppState(onboardingComplete = true, sites = SiteBook().add(home, makePrimary = true), digestEnabled = false),
            report,
        )
        graph.settings.update { armed }
        WindowAlarmScheduler.reschedule(context, armed)

        WindowAlarmScheduler.fire(context)

        assertNull("firing clears the one-shot reminder", graph.settings.current().windowAlarm)
        val posted = notifications(context).activeNotifications
        assertEquals("expected one notification, got ${posted.size}", 1, posted.size)
        val notification = posted.single().notification
        assertEquals(DigestNotifier.CHANNEL_WINDOW, notification.channelId)
        assertEquals(
            "The window is open at Home",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        val contentIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(home.id, contentIntent.getStringExtra(DigestNotifier.EXTRA_SITE_ID))

        notifications(context).cancelAll()
        WindowAlarmScheduler.fire(context)
        assertEquals("a cleared one-shot reminder must not post twice", 0, notifications(context).activeNotifications.size)
    }

    private fun windowScheduledAlarm(alarms: ShadowAlarmManager): ShadowAlarmManager.ScheduledAlarm? =
        alarms.scheduledAlarms.firstOrNull { scheduled ->
            val pending = scheduled.operation ?: return@firstOrNull false
            shadowOf(pending).requestCode == WindowAlarmScheduler.ALARM_REQUEST_CODE &&
                shadowOf(pending).savedIntent.action == WindowAlarmScheduler.ACTION_WINDOW_ALARM
        }

    private fun clearReport(): NightReport {
        val start = Instant.parse("2024-08-09T00:00:00Z").epochSecond
        val forecast = Forecast(
            home.latitude,
            home.longitude,
            "gem_seamless",
            start,
            (0 until 72).map {
                HourlyWeather(
                    epochSecond = start + it * 3600L,
                    cloudCover = 0,
                    humidity = 45,
                    windKmh = 6.0,
                    gustKmh = 10.0,
                    jetStreamKmh = 40.0,
                    seeing = 4,
                    transparency = 4,
                )
            },
        )
        return NightPlanner.plan(home, date, forecast, kit)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun notifications(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun initWorkManager(context: Context) {
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder()
                    .setExecutor(SynchronousExecutor())
                    .setTaskExecutor(SynchronousExecutor())
                    .build(),
            )
        }
    }
}
