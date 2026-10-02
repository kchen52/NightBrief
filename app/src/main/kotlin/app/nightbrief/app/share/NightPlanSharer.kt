package app.nightbrief.app.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import app.nightbrief.app.R
import app.nightbrief.score.NightPlanCard
import app.nightbrief.score.NightReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Builds a share intent for one night.
 *
 * Returns null when the PNG cannot be written; the caller then does nothing.
 * The image and the caption use [NightPlanCard], so the site pin is not included.
 */
object NightPlanSharer {
    private const val FILE_NAME = "night-plan.png"

    fun chooserIntent(context: Context, report: NightReport): Intent? {
        val send = sendIntent(context, report) ?: return null
        return Intent.createChooser(send, context.getString(R.string.share_night_plan)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = send.clipData
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun sendIntent(context: Context, report: NightReport): Intent? {
        val card = NightPlanCard.from(report)
        val file = writePng(context, card) ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, card.caption)
            clipData = ClipData.newUri(context.contentResolver, card.siteName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writePng(context: Context, card: NightPlanCard): File? {
        val dir = File(context.cacheDir, "share")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val dest = File(dir, FILE_NAME)
        val tmp = File(dir, "$FILE_NAME.tmp")
        return try {
            val bitmap = NightPlanImage.render(card)
            val wrote = FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
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
        }
    }
}

/**
 * Writes the image off the main thread, then opens the share sheet.
 * No-ops when the PNG cannot be written or nothing can receive the intent.
 */
suspend fun shareNightPlan(context: Context, report: NightReport) {
    val intent = withContext(Dispatchers.IO) {
        NightPlanSharer.chooserIntent(context, report)
    } ?: return
    withContext(Dispatchers.Main.immediate) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: IllegalStateException) {
        }
    }
}
