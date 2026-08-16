package com.sousoulab.einklauncher

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal data class VerifiedUpdate(
    val file: File,
    val release: UpdateRelease,
)

/** Verifies that a downloaded APK is a strictly newer build signed like this installation. */
internal class UpdatePackageVerifier(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val packageManager = appContext.packageManager
    private val packageName = appContext.packageName
    private val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY_NAME)

    @Throws(UpdateException::class)
    fun currentVersionName(): String = installedPackageInfo(flags = 0L)
        .versionName
        ?.takeIf { it.isNotBlank() }
        ?: throw UpdateException("Installed app has no version name")

    @Throws(UpdateException::class)
    fun verify(file: File, release: UpdateRelease): VerifiedUpdate {
        validateReleaseVersion(release)
        val candidate = validateUpdateFile(file, release)
        val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES.toLong()
        }
        val installed = installedPackageInfo(signingFlags)
        val archive = archivePackageInfo(candidate, signingFlags)

        if (archive.packageName != packageName) {
            throw UpdateException("Downloaded APK package name does not match")
        }
        val archiveVersionName = archive.versionName
            ?: throw UpdateException("Downloaded APK has no version name")
        if (archiveVersionName != release.versionName) {
            throw UpdateException("Downloaded APK version name does not match the release tag")
        }

        val installedVersionName = installed.versionName
            ?: throw UpdateException("Installed app has no version name")
        if (!UpdatePolicy.isNewer(release.versionName, installedVersionName)) {
            throw UpdateException("Downloaded APK version name is not newer")
        }

        val installedVersionCode = installed.longVersionCodeCompat()
        val archiveVersionCode = archive.longVersionCodeCompat()
        if (archiveVersionCode <= installedVersionCode) {
            throw UpdateException("Downloaded APK version code is not strictly newer")
        }

        val installedSigners = installed.currentSignerDigests()
        val archiveSigners = archive.currentSignerDigests()
        if (installedSigners != archiveSigners) {
            throw UpdateException("Downloaded APK signing certificates do not match")
        }

        return VerifiedUpdate(file = candidate, release = release)
    }

    private fun validateReleaseVersion(release: UpdateRelease) {
        val normalizedTag = UpdatePolicy.normalizedVersion(release.tagName)
            ?: throw UpdateException("Release tag is not a semantic version")
        if (normalizedTag != release.versionName) {
            throw UpdateException("Release version does not match its tag")
        }
        if (UpdatePolicy.normalizedVersion(release.versionName) != release.versionName) {
            throw UpdateException("Release version name is not normalized")
        }
    }

    private fun validateUpdateFile(file: File, release: UpdateRelease): File {
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
        if (candidate.name != release.apkAsset.name || !candidate.name.endsWith(".apk")) {
            throw UpdateException("Downloaded APK file name does not match the release")
        }
        if (!candidate.isFile || candidate.length() <= 0L) {
            throw UpdateException("Downloaded APK is missing or empty")
        }
        if (candidate.length() > UpdatePolicy.MAX_APK_BYTES) {
            throw UpdateException("Downloaded APK exceeds the size limit")
        }
        if (
            release.apkAsset.sizeBytes > 0L &&
            candidate.length() != release.apkAsset.sizeBytes
        ) {
            throw UpdateException("Downloaded APK size does not match the release")
        }
        return candidate
    }

    private fun installedPackageInfo(flags: Long): PackageInfo = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags.toInt())
        }
    } catch (error: PackageManager.NameNotFoundException) {
        throw UpdateException("Installed app package information is unavailable", error)
    } catch (error: RuntimeException) {
        throw UpdateException("Installed app package information could not be read", error)
    }

    private fun archivePackageInfo(file: File, flags: Long): PackageInfo {
        val archive = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(flags),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(file.absolutePath, flags.toInt())
            }
        } catch (error: RuntimeException) {
            throw UpdateException("Downloaded APK could not be parsed", error)
        }
        return archive ?: throw UpdateException("Downloaded file is not a valid APK")
    }

    private fun PackageInfo.currentSignerDigests(): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
                ?: throw UpdateException("APK signing certificates are unavailable")
        } else {
            @Suppress("DEPRECATION")
            this.signatures ?: throw UpdateException("APK signing certificates are unavailable")
        }
        if (signatures.isEmpty()) throw UpdateException("APK has no signing certificates")
        return signatures.mapTo(mutableSetOf<String>()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private companion object {
        const val UPDATE_DIRECTORY_NAME = "updates"
    }
}
