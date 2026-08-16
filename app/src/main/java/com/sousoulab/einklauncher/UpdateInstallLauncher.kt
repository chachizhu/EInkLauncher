package com.sousoulab.einklauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Builds user-visible system intents; it never installs an APK silently. */
internal class SystemUpdateInstaller(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val packageManager = appContext.packageManager
    private val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY_NAME)

    fun canRequestInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

    @Throws(UpdateException::class)
    fun installIntent(file: File): Intent {
        if (!canRequestInstall()) {
            throw UpdateException("Permission to install unknown apps has not been granted")
        }
        val candidate = validatedUpdateFile(file)
        val contentUri = try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.update-files",
                candidate,
            )
        } catch (error: IllegalArgumentException) {
            throw UpdateException("Downloaded APK cannot be shared with the system installer", error)
        } catch (error: SecurityException) {
            throw UpdateException("Downloaded APK access was denied", error)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            throw UpdateException("System package installer is unavailable")
        }
        return intent
    }

    private fun validatedUpdateFile(file: File): File {
        val expectedDirectory = try {
            updateDirectory.canonicalFile
        } catch (error: IOException) {
            throw UpdateException("Update cache directory could not be resolved", error)
        }
        val candidate = try {
            file.canonicalFile
        } catch (error: IOException) {
            throw UpdateException("Downloaded APK path could not be resolved", error)
        }
        if (candidate.parentFile != expectedDirectory) {
            throw UpdateException("Downloaded APK is outside the update cache directory")
        }
        if (!candidate.isFile || candidate.length() <= 0L || !candidate.name.endsWith(".apk")) {
            throw UpdateException("Downloaded APK is missing or invalid")
        }
        return candidate
    }

    private companion object {
        const val UPDATE_DIRECTORY_NAME = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
