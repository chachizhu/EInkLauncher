package com.sousoulab.einklauncher

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal interface ReleaseSource {
    @Throws(UpdateException::class)
    fun latestRelease(): UpdateRelease
}

internal interface ApkDownloader {
    @Throws(UpdateException::class)
    fun download(release: UpdateRelease, updateDirectory: File): File
}

internal interface UpdateClient : ReleaseSource, ApkDownloader {
    fun cancel()
}

/** Blocking transport used only from the Activity's user-initiated update thread. */
internal class GitHubReleaseClient : UpdateClient {
    @Volatile
    private var cancelled = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override fun latestRelease(): UpdateRelease = wrapFailure("Update check failed") {
        val releasePageUrl = latestReleasePageUrl()
        val tagName = UpdatePolicy.releaseTagFromPageUrl(releasePageUrl)
            ?: throw UpdateException("Latest release redirect is invalid")
        val versionName = UpdatePolicy.normalizedVersion(tagName)
            ?: throw UpdateException("Release tag is not a semantic version")
        val expectedApkName = UpdatePolicy.expectedApkName(tagName)
            ?: throw UpdateException("Release APK name cannot be determined")
        val expectedChecksumName = UpdatePolicy.expectedChecksumName(tagName)
            ?: throw UpdateException("Release checksum name cannot be determined")

        val downloadUrl = UpdatePolicy.assetUrl(tagName, expectedApkName)
            ?: throw UpdateException("Release APK URL cannot be determined")
        val checksumUrl = UpdatePolicy.assetUrl(tagName, expectedChecksumName)
            ?: throw UpdateException("Release checksum URL cannot be determined")

        UpdateRelease(
            tagName = tagName,
            versionName = versionName,
            releasePageUrl = releasePageUrl,
            apkAsset = UpdateAsset(
                name = expectedApkName,
                downloadUrl = downloadUrl,
                sizeBytes = UpdatePolicy.UNKNOWN_ASSET_SIZE_BYTES,
                apiSha256 = null,
                checksumUrl = checksumUrl,
            ),
        )
    }

    override fun download(release: UpdateRelease, updateDirectory: File): File =
        wrapFailure("Update download failed") {
            downloadVerified(release, updateDirectory)
        }

    private fun downloadVerified(release: UpdateRelease, updateDirectory: File): File {
        ensureActive()
        validateDownloadRequest(release)
        val expectedSha256 = resolveExpectedSha256(release)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw UpdateException("Update cache directory could not be created")
        }
        if (!updateDirectory.isDirectory) throw UpdateException("Update cache path is not a directory")

        val destination = File(updateDirectory, release.apkAsset.name)
        val partial = try {
            File.createTempFile("${release.apkAsset.name}.", ".part", updateDirectory)
        } catch (error: Exception) {
            throw UpdateException("Partial update file could not be created", error)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var downloadedBytes = 0L
        var responseSizeBytes = UpdatePolicy.UNKNOWN_ASSET_SIZE_BYTES
        try {
            withConnection(
                initialUrl = release.apkAsset.downloadUrl,
                allowAssetRedirects = true,
                accept = "application/octet-stream",
            ) { connection ->
                requireSuccess(connection)
                responseSizeBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    @Suppress("DEPRECATION")
                    connection.contentLength.toLong()
                }
                if (responseSizeBytes > UpdatePolicy.MAX_APK_BYTES) {
                    throw UpdateException("Release APK exceeds the size limit")
                }
                connection.inputStream.use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            downloadedBytes += count
                            if (downloadedBytes > UpdatePolicy.MAX_APK_BYTES) {
                                throw UpdateException("Downloaded APK exceeds the size limit")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            ensureActive()
            val expectedSizeBytes = release.apkAsset.sizeBytes
                .takeIf { it > 0L }
                ?: responseSizeBytes.takeIf { it > 0L }
            if (expectedSizeBytes != null && downloadedBytes != expectedSizeBytes) {
                throw UpdateException("Downloaded APK size does not match the release")
            }
            val actualSha256 = digest.digest().toHexString()
            if (actualSha256 != expectedSha256) throw UpdateException("Downloaded APK digest does not match")

            ensureActive()
            if (destination.exists() && !destination.delete()) {
                throw UpdateException("Old verified update could not be removed")
            }
            ensureActive()
            if (!partial.renameTo(destination)) throw UpdateException("Verified update could not be finalized")
            return destination
        } catch (error: Exception) {
            partial.delete()
            if (error is UpdateException || error is UpdateCancelledException) throw error
            throw UpdateException("Update download failed", error)
        }
    }

    override fun cancel() {
        cancelled = true
        activeConnection?.disconnect()
    }

    private fun resolveExpectedSha256(release: UpdateRelease): String {
        val asset = release.apkAsset
        asset.apiSha256?.let { return it }
        val checksumSha256 = asset.checksumUrl?.let { checksumUrl ->
            val checksumText = readUrl(
                url = checksumUrl,
                maximumBytes = MAX_CHECKSUM_BYTES,
                allowAssetRedirects = true,
                accept = "application/octet-stream",
            )
            UpdatePolicy.parseChecksumFile(checksumText, asset.name)
                ?: throw UpdateException("Release checksum file is invalid")
        }
        return checksumSha256 ?: throw UpdateException("Release has no SHA-256 digest")
    }

    private fun latestReleasePageUrl(): String {
        val initialUrl = UpdatePolicy.LATEST_RELEASE_URL
        ensureActive()
        val connection = (URL(initialUrl).openConnection() as? HttpsURLConnection)
            ?: throw UpdateException("Only HTTPS update URLs are allowed")
        configureConnection(connection, accept = "text/html")
        activeConnection = connection
        try {
            ensureActive()
            val status = connection.responseCode
            if (status !in REDIRECT_STATUS_CODES) {
                throw UpdateException("GitHub latest release did not redirect")
            }
            val location = connection.getHeaderField("Location")
                ?: throw UpdateException("GitHub latest release redirect is missing")
            val releasePageUrl = URI(initialUrl).resolve(location).toString()
            if (UpdatePolicy.releaseTagFromPageUrl(releasePageUrl) == null) {
                throw UpdateException("GitHub latest release redirect is not allowed")
            }
            return releasePageUrl
        } catch (error: Exception) {
            if (cancelled || Thread.currentThread().isInterrupted) {
                throw UpdateCancelledException()
            }
            throw error
        } finally {
            if (activeConnection === connection) activeConnection = null
            connection.disconnect()
        }
    }

    private fun readUrl(
        url: String,
        maximumBytes: Long,
        allowAssetRedirects: Boolean,
        accept: String,
    ): String =
        withConnection(url, allowAssetRedirects, accept) { connection ->
            requireSuccess(connection)
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    if (total > maximumBytes) throw UpdateException("Server response is too large")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }

    private fun <T> withConnection(
        initialUrl: String,
        allowAssetRedirects: Boolean,
        accept: String,
        block: (HttpsURLConnection) -> T,
    ): T {
        var currentUrl = initialUrl
        var redirectCount = 0
        while (true) {
            ensureActive()
            val connection = (URL(currentUrl).openConnection() as? HttpsURLConnection)
                ?: throw UpdateException("Only HTTPS update URLs are allowed")
            configureConnection(connection, accept)
            activeConnection = connection
            try {
                // Closes the race where cancellation happens just before publication above.
                ensureActive()
                val status = connection.responseCode
                if (status in REDIRECT_STATUS_CODES) {
                    if (!allowAssetRedirects || redirectCount >= MAX_REDIRECTS) {
                        throw UpdateException("Update redirect is not allowed")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw UpdateException("Update redirect has no destination")
                    val redirectedUrl = URI(currentUrl).resolve(location).toString()
                    if (!UpdatePolicy.isAllowedAssetRedirectUrl(redirectedUrl)) {
                        throw UpdateException("Update redirect destination is not allowed")
                    }
                    currentUrl = redirectedUrl
                    redirectCount += 1
                    continue
                }
                return block(connection)
            } catch (error: Exception) {
                if (cancelled || Thread.currentThread().isInterrupted) {
                    throw UpdateCancelledException()
                }
                throw error
            } finally {
                if (activeConnection === connection) activeConnection = null
                connection.disconnect()
            }
        }
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            throw UpdateException("GitHub returned HTTP $status")
        }
    }

    private fun configureConnection(connection: HttpsURLConnection, accept: String) {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "EInkLauncher-Android")
        connection.setRequestProperty("Accept", accept)
    }

    private fun ensureActive() {
        if (cancelled || Thread.currentThread().isInterrupted) {
            throw UpdateCancelledException()
        }
    }

    private fun validateDownloadRequest(release: UpdateRelease) {
        val expectedName = UpdatePolicy.expectedApkName(release.tagName)
        if (expectedName == null || release.apkAsset.name != expectedName) {
            throw UpdateException("Release APK name is invalid")
        }
        if (
            !UpdatePolicy.isAllowedAssetUrl(
                release.apkAsset.downloadUrl,
                release.tagName,
                release.apkAsset.name,
            )
        ) {
            throw UpdateException("Release APK URL is not allowed")
        }
        if (
            release.apkAsset.sizeBytes != UpdatePolicy.UNKNOWN_ASSET_SIZE_BYTES &&
            release.apkAsset.sizeBytes !in 1..UpdatePolicy.MAX_APK_BYTES
        ) {
            throw UpdateException("Release APK size is invalid")
        }
        release.apkAsset.checksumUrl?.let { checksumUrl ->
            val checksumName = UpdatePolicy.expectedChecksumName(release.tagName)
                ?: throw UpdateException("Release checksum name is invalid")
            if (!UpdatePolicy.isAllowedAssetUrl(checksumUrl, release.tagName, checksumName)) {
                throw UpdateException("Release checksum URL is not allowed")
            }
        }
    }

    private inline fun <T> wrapFailure(message: String, block: () -> T): T = try {
        block()
    } catch (error: UpdateCancelledException) {
        throw error
    } catch (error: UpdateException) {
        throw error
    } catch (error: Exception) {
        throw UpdateException(message, error)
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_CHECKSUM_BYTES = 4L * 1024L
        const val MAX_REDIRECTS = 3
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class UpdateCancelledException : Exception("Update request was cancelled")
