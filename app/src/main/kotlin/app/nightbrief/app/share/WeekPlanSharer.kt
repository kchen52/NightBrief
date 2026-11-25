package app.nightbrief.app.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import app.nightbrief.app.R
import app.nightbrief.score.WeekPlanCard
import app.nightbrief.score.WeeklyOutlook
import app.nightbrief.weather.ForecastStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant

/**
 * Builds a share intent for one site's week outlook.
 *
 * Returns null when the PNG cannot be written; the caller then does nothing.
 * The image and the caption use [WeekPlanCard], so the site pin is not included.
 */
object WeekPlanSharer {
    private const val FILE_NAME = "week-plan.png"

    fun chooserIntent(
        context: Context,
        outlook: WeeklyOutlook,
        forecastStatus: ForecastStatus? = null,
        forecastFetchedAt: Instant? = null,
    ): Intent? {
        val send = sendIntent(context, outlook, forecastStatus, forecastFetchedAt) ?: return null
        return Intent.createChooser(send, context.getString(R.string.share_week)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun sendIntent(
        context: Context,
        outlook: WeeklyOutlook,
        forecastStatus: ForecastStatus? = null,
        forecastFetchedAt: Instant? = null,
    ): Intent? {
        val card = WeekPlanCard.from(outlook, forecastStatus, forecastFetchedAt)
        val file = writePng(context, card) ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, card.caption)
            clipData = ClipData.newRawUri(card.siteName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writePng(context: Context, card: WeekPlanCard): File? {
        val dir = File(context.cacheDir, "share")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val dest = File(dir, FILE_NAME)
        val tmp = File(dir, "$FILE_NAME.tmp")
        val bitmap = try {
            WeekPlanImage.render(card)
        } catch (_: Throwable) {
            tmp.delete()
            return null
        }
        return try {
            val wrote = FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!wrote) {
                tmp.delete()
                return null
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            dest
        } catch (_: IOException) {
            tmp.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Writes the week image off the main thread, then opens the share sheet.
 * No-ops when the PNG cannot be written or nothing can receive the intent.
 */
suspend fun shareWeekPlan(
    context: Context,
    outlook: WeeklyOutlook,
    forecastStatus: ForecastStatus? = null,
    forecastFetchedAt: Instant? = null,
): Boolean {
    val intent = withContext(Dispatchers.IO) {
        WeekPlanSharer.chooserIntent(context, outlook, forecastStatus, forecastFetchedAt)
    } ?: return false
    return try {
        withContext(Dispatchers.Main.immediate) {
            context.startActivity(intent)
        }
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalStateException) {
        false
    }
}
