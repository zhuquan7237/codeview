package com.zhuquan.codeview

import com.zhuquan.codeview.core.AppUpdate
import com.zhuquan.codeview.core.ReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version comparison and the two manifests the updater reads. */
class AppUpdateTest {

    // --- nothing about the current version may ever trigger an "update" ---------
    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(AppUpdate.isNewer("1.2.0", "1.2.0"))
        assertFalse(AppUpdate.isNewer("v1.2.0", "1.2.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(AppUpdate.isNewer("1.1.0", "1.2.0"))
        assertFalse(AppUpdate.isNewer("1.2", "1.2.0"))
    }

    @Test
    fun newerVersionWinsSegmentBySegment() {
        assertTrue(AppUpdate.isNewer("1.2.1", "1.2.0"))
        assertTrue(AppUpdate.isNewer("1.3.0", "1.2.9"))
        // 1.10 is newer than 1.9 — the classic string-comparison trap.
        assertTrue(AppUpdate.isNewer("1.10.0", "1.9.9"))
        assertTrue(AppUpdate.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun versionPartsTolerateRealWorldTags() {
        assertEquals(listOf(1, 2, 0), AppUpdate.versionParts("v1.2.0"))
        assertEquals(listOf(1, 2, 3), AppUpdate.versionParts("1.2.3-beta1"))
        assertEquals(emptyList<Int>(), AppUpdate.versionParts("latest"))
    }

    @Test
    fun garbageCandidateNeverCountsAsUpdate() {
        assertFalse(AppUpdate.isNewer("", "1.2.0"))
        assertFalse(AppUpdate.isNewer("latest", "1.2.0"))
    }

    // --- our own manifest ------------------------------------------------------
    @Test
    fun parsesOurManifest() {
        val json = """
            {"version":"1.3.0","versionCode":4,
             "apkUrl":"https://relay.zhuquan.xyz/dl/codeview-1.3.0.apk",
             "sha256":"abc123","size":1103495,"notes":"修了什么"}
        """.trimIndent()
        val info = AppUpdate.parseManifest(json, "relay")
        assertEquals("1.3.0", info?.version)
        assertEquals("https://relay.zhuquan.xyz/dl/codeview-1.3.0.apk", info?.apkUrl)
        assertEquals("abc123", info?.sha256)
        assertEquals(1103495L, info?.size)
        assertEquals("修了什么", info?.notes)
        assertEquals("relay", info?.source)
    }

    @Test
    fun manifestWithoutVersionOrUrlIsRejected() {
        assertNull(AppUpdate.parseManifest("""{"apkUrl":"https://x/a.apk"}"""))
        assertNull(AppUpdate.parseManifest("""{"version":"1.3.0"}"""))
        // A relative URL would make the installer download nothing.
        assertNull(AppUpdate.parseManifest("""{"version":"1.3.0","apkUrl":"/dl/a.apk"}"""))
        assertNull(AppUpdate.parseManifest("not json at all"))
    }

    // --- GitHub fallback -------------------------------------------------------
    @Test
    fun parsesGithubReleaseAndPicksTheApkAsset() {
        val json = """
            {"tag_name":"v1.3.0","body":"notes here","assets":[
              {"name":"checksums.txt","browser_download_url":"https://x/checksums.txt","size":10},
              {"name":"codeview-1.3.0.apk","browser_download_url":"https://x/codeview-1.3.0.apk",
               "size":1103495,"digest":"sha256:deadbeef"}
            ]}
        """.trimIndent()
        val info = AppUpdate.parseGithub(json)
        assertEquals("v1.3.0", info?.version)
        assertEquals("https://x/codeview-1.3.0.apk", info?.apkUrl)
        assertEquals("deadbeef", info?.sha256)
        assertEquals(1103495L, info?.size)
        assertEquals("github", info?.source)
    }

    @Test
    fun githubReleaseWithoutApkAssetIsIgnored() {
        assertNull(AppUpdate.parseGithub("""{"tag_name":"v1.3.0","assets":[{"name":"a.zip","browser_download_url":"https://x/a.zip"}]}"""))
        assertNull(AppUpdate.parseGithub("""{"tag_name":"v1.3.0"}"""))
    }

    // --- picking among sources -------------------------------------------------
    @Test
    fun highestVersionWinsAcrossSources() {
        val candidates = listOf(
            ReleaseInfo(version = "1.2.1", apkUrl = "https://a", source = "cn"),
            ReleaseInfo(version = "1.3.0", apkUrl = "https://b", source = "relay"),
            ReleaseInfo(version = "1.0.9", apkUrl = "https://c", source = "github"),
        )
        assertEquals("1.3.0", AppUpdate.pick(candidates, "1.2.0")?.version)
    }

    @Test
    fun staleCandidatesAreIgnored() {
        val candidates = listOf(
            ReleaseInfo(version = "1.2.0", apkUrl = "https://a"),
            ReleaseInfo(version = "1.1.0", apkUrl = "https://b"),
        )
        assertNull(AppUpdate.pick(candidates, "1.2.0"))
        assertNull(AppUpdate.pick(emptyList(), "1.2.0"))
    }

    @Test
    fun sizeFormatting() {
        assertEquals("未知大小", AppUpdate.formatSize(0))
        assertEquals("1.05 MB", AppUpdate.formatSize(1_103_495))
        assertEquals("512 KB", AppUpdate.formatSize(524_288))
    }
}
