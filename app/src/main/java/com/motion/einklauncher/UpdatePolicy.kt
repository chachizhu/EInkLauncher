package com.motion.einklauncher

import java.net.URI

internal data class UpdateRelease(
    val tagName: String,
    val versionName: String,
    val releasePageUrl: String,
    val apkAsset: UpdateAsset,
)

internal data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val apiSha256: String?,
    val checksumUrl: String?,
)

/** Pure validation rules for version tags and release assets. */
internal object UpdatePolicy {
    const val LATEST_RELEASE_URL =
        "https://github.com/chachizhu/EInkLauncher/releases/latest"
    const val UNKNOWN_ASSET_SIZE_BYTES = -1L
    const val MAX_APK_BYTES = 32L * 1024L * 1024L

    private const val RELEASE_DOWNLOAD_PATH_PREFIX =
        "/chachizhu/EInkLauncher/releases/download/"
    private const val RELEASE_PAGE_PATH_PREFIX =
        "/chachizhu/EInkLauncher/releases/tag/"
    private val versionPattern = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun normalizedVersion(value: String): String? {
        val match = versionPattern.matchEntire(value.trim()) ?: return null
        val parts = match.groupValues.drop(1).map { part ->
            part.toLongOrNull() ?: return null
        }
        return parts.joinToString(".")
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate) ?: return false
        val currentParts = versionParts(current) ?: return false
        return candidateParts.zip(currentParts).firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left > right }
            ?: false
    }

    fun expectedApkName(tagName: String): String? =
        normalizedVersion(tagName)?.let { "EInkLauncher-$tagName.apk" }

    fun expectedChecksumName(tagName: String): String? =
        expectedApkName(tagName)?.let { "$it.sha256" }

    fun releaseTagFromPageUrl(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (
            uri.scheme != "https" ||
            uri.host != "github.com" ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.rawQuery != null ||
            uri.rawFragment != null
        ) {
            return null
        }
        val rawPath = uri.rawPath ?: return null
        if (!rawPath.startsWith(RELEASE_PAGE_PATH_PREFIX)) return null
        val tagName = rawPath.removePrefix(RELEASE_PAGE_PATH_PREFIX)
        if (tagName.isBlank() || '/' in tagName) return null
        return tagName.takeIf { normalizedVersion(it) != null }
    }

    fun assetUrl(tagName: String, assetName: String): String? {
        if (normalizedVersion(tagName) == null || assetName.isBlank() || '/' in assetName) return null
        return "https://github.com$RELEASE_DOWNLOAD_PATH_PREFIX$tagName/$assetName"
    }

    fun normalizeSha256(value: String?): String? {
        val digest = value
            ?.trim()
            ?.removePrefix("sha256:")
            ?.trim()
            ?: return null
        return digest.lowercase().takeIf(sha256Pattern::matches)
    }

    fun parseChecksumFile(contents: String, expectedApkName: String): String? {
        val line = contents.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val parts = line.split(Regex("\\s+"), limit = 2)
        val digest = normalizeSha256(parts.firstOrNull()) ?: return null
        if (parts.size == 2) {
            val fileName = parts[1].trim().removePrefix("*")
            if (fileName != expectedApkName) return null
        }
        return digest
    }

    fun isAllowedAssetUrl(url: String, tagName: String, assetName: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (
            uri.scheme != "https" ||
            uri.host != "github.com" ||
            uri.userInfo != null ||
            uri.port != -1
        ) {
            return false
        }
        val expectedPath = "$RELEASE_DOWNLOAD_PATH_PREFIX$tagName/$assetName"
        return uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == expectedPath
    }

    fun isAllowedAssetRedirectUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host == "release-assets.githubusercontent.com" &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.rawFragment == null &&
            !uri.rawPath.isNullOrBlank()
    }

    private fun versionParts(value: String): List<Long>? {
        val normalized = normalizedVersion(value) ?: return null
        return normalized.split('.').map { it.toLongOrNull() ?: return null }
    }
}
