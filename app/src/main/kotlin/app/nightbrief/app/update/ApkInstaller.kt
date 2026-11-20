package app.nightbrief.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * Hands a downloaded APK to Android's package installer after verifying its
 * package name, version code, and signing certificate against the installed app.
 *
 * Android always shows its own confirm sheet; this only skips the browser and
 * Downloads folder. Returns false when verification fails or nothing can handle
 * the install intent.
 */
object ApkInstaller {
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Settings screen unavailable on this device.
        }
    }

    /**
     * Checks that [apk] belongs to this app, is not a downgrade, and is signed by
     * the same certificate set as the installed build.
     */
    fun verifyApk(context: Context, apk: File): Boolean {
        val length = apk.length()
        if (!apk.isFile || length <= 0L || length > GithubReleaseClient.MAX_APK_BYTES) return false
        val pm = context.packageManager
        val installed = getInstalledPackageInfo(pm, context.packageName) ?: return false
        val archive = getArchivePackageInfo(pm, apk) ?: return false
        if (archive.packageName != context.packageName) return false
        if (PackageInfoCompat.getLongVersionCode(archive) < PackageInfoCompat.getLongVersionCode(installed)) {
            return false
        }
        val installedDigests = signingCertDigests(installed)
        val archiveDigests = signingCertDigests(archive)
        return installedDigests.isNotEmpty() && installedDigests == archiveDigests
    }

    fun install(context: Context, apk: File): Boolean {
        if (!verifyApk(context, apk)) {
            apk.delete()
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? =
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                else ->
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun getArchivePackageInfo(pm: PackageManager, apk: File): PackageInfo? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                pm.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            else ->
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        }

    private fun signingCertDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            signingInfo.apkContentsSigners?.takeIf { it.isNotEmpty() }
                ?: signingInfo.signingCertificateHistory
                ?: return emptySet()
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return emptySet()
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
        return signatures.mapTo(LinkedHashSet()) { sig ->
            sha256.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
