package com.sousoulab.einklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val apkName = "EInkLauncher-v1.2.3.apk"
    private val uppercaseSha256 = "AB".repeat(32)
    private val lowercaseSha256 = uppercaseSha256.lowercase()

    @Test
    fun `semantic versions are normalized as numeric components`() {
        assertEquals("1.2.3", UpdatePolicy.normalizedVersion(" v01.002.0003 "))
        assertEquals("1.2.3", UpdatePolicy.normalizedVersion("V1.2.3"))
        assertNull(UpdatePolicy.normalizedVersion("v1.2"))
        assertNull(UpdatePolicy.normalizedVersion("v1.2.3-beta"))
        assertNull(UpdatePolicy.normalizedVersion("v9223372036854775808.0.0"))
    }

    @Test
    fun `newer versions are compared numerically instead of lexicographically`() {
        assertTrue(UpdatePolicy.isNewer(candidate = "v1.10.0", current = "v1.9.9"))
        assertTrue(UpdatePolicy.isNewer(candidate = "2.0.0", current = "v1.999.999"))
        assertFalse(UpdatePolicy.isNewer(candidate = "v1.2.3", current = "1.2.3"))
        assertFalse(UpdatePolicy.isNewer(candidate = "v1.2.2", current = "v1.2.3"))
        assertFalse(UpdatePolicy.isNewer(candidate = "latest", current = "v1.2.3"))
    }

    @Test
    fun `release asset names are derived exactly from a valid tag`() {
        assertEquals(apkName, UpdatePolicy.expectedApkName("v1.2.3"))
        assertEquals("$apkName.sha256", UpdatePolicy.expectedChecksumName("v1.2.3"))
        assertEquals(
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            UpdatePolicy.assetUrl("v1.2.3", apkName),
        )
        assertNull(UpdatePolicy.expectedApkName("v1.2.3-beta"))
        assertNull(UpdatePolicy.expectedChecksumName("v1.2"))
        assertNull(UpdatePolicy.assetUrl("v1.2", apkName))
        assertNull(UpdatePolicy.assetUrl("v1.2.3", "directory/$apkName"))
    }

    @Test
    fun `latest release page URL yields only an exact GitHub semantic tag`() {
        assertEquals(
            "v1.2.3",
            UpdatePolicy.releaseTagFromPageUrl(
                "https://github.com/TaoZang/EInkLauncher/releases/tag/v1.2.3",
            ),
        )
        val rejectedUrls = listOf(
            "http://github.com/TaoZang/EInkLauncher/releases/tag/v1.2.3",
            "https://github.com/SomeoneElse/EInkLauncher/releases/tag/v1.2.3",
            "https://github.com/TaoZang/EInkLauncher/releases/tag/v1.2.3?download=1",
            "https://github.com/TaoZang/EInkLauncher/releases/tag/v1.2.3/extra",
            "https://github.com/TaoZang/EInkLauncher/releases/tag/latest",
            "https://user@github.com/TaoZang/EInkLauncher/releases/tag/v1.2.3",
        )
        rejectedUrls.forEach { url ->
            assertNull(url, UpdatePolicy.releaseTagFromPageUrl(url))
        }
    }

    @Test
    fun `only exact GitHub release asset URLs are allowed`() {
        val apkUrl =
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName"
        val checksumName = "$apkName.sha256"
        val checksumUrl =
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$checksumName"

        assertTrue(UpdatePolicy.isAllowedAssetUrl(apkUrl, "v1.2.3", apkName))
        assertTrue(UpdatePolicy.isAllowedAssetUrl(checksumUrl, "v1.2.3", checksumName))
    }

    @Test
    fun `asset URL allowlist rejects transport host path and suffix changes`() {
        val rejectedUrls = listOf(
            "http://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://github.com.evil.example/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://api.github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://github.com/SomeoneElse/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.4/$apkName",
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/other.apk",
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName?raw=1",
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName#download",
            "https://user@github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://github.com:443/TaoZang/EInkLauncher/releases/download/v1.2.3/$apkName",
            "https://github.com/TaoZang/EInkLauncher/releases/download/v1.2.3/%45InkLauncher-v1.2.3.apk",
        )

        rejectedUrls.forEach { url ->
            assertFalse(url, UpdatePolicy.isAllowedAssetUrl(url, "v1.2.3", apkName))
        }
    }

    @Test
    fun `asset redirects only allow the dedicated GitHub release CDN`() {
        assertTrue(
            UpdatePolicy.isAllowedAssetRedirectUrl(
                "https://release-assets.githubusercontent.com/github-production-release-asset/id?sig=value",
            ),
        )
        val rejectedUrls = listOf(
            "http://release-assets.githubusercontent.com/file?sig=value",
            "https://release-assets.githubusercontent.com.evil.example/file?sig=value",
            "https://user@release-assets.githubusercontent.com/file?sig=value",
            "https://release-assets.githubusercontent.com:443/file?sig=value",
            "https://objects.githubusercontent.com/file?sig=value",
            "https://release-assets.githubusercontent.com/file?sig=value#fragment",
        )
        rejectedUrls.forEach { url ->
            assertFalse(url, UpdatePolicy.isAllowedAssetRedirectUrl(url))
        }
    }

    @Test
    fun `SHA-256 values accept GitHub prefix and normalize hex case`() {
        assertEquals(lowercaseSha256, UpdatePolicy.normalizeSha256(uppercaseSha256))
        assertEquals(
            lowercaseSha256,
            UpdatePolicy.normalizeSha256("  sha256:$uppercaseSha256  "),
        )
        assertNull(UpdatePolicy.normalizeSha256(null))
        assertNull(UpdatePolicy.normalizeSha256("sha256:${"a".repeat(63)}"))
        assertNull(UpdatePolicy.normalizeSha256("sha512:$uppercaseSha256"))
        assertNull(UpdatePolicy.normalizeSha256("${"g".repeat(64)}"))
    }

    @Test
    fun `checksum files bind the digest to the exact APK name`() {
        assertEquals(
            lowercaseSha256,
            UpdatePolicy.parseChecksumFile("$uppercaseSha256  $apkName\n", apkName),
        )
        assertEquals(
            lowercaseSha256,
            UpdatePolicy.parseChecksumFile("$uppercaseSha256 *$apkName\n", apkName),
        )
        assertEquals(
            lowercaseSha256,
            UpdatePolicy.parseChecksumFile("\nsha256:$uppercaseSha256\n", apkName),
        )
        assertNull(
            UpdatePolicy.parseChecksumFile("$uppercaseSha256  EInkLauncher-v1.2.4.apk\n", apkName),
        )
        assertNull(UpdatePolicy.parseChecksumFile("not-a-digest  $apkName\n", apkName))
        assertNull(UpdatePolicy.parseChecksumFile("\n\n", apkName))
    }
}
