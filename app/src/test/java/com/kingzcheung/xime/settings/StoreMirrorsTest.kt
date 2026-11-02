package com.kingzcheung.xime.settings

import com.kingzcheung.xime.model.DownloadMirrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMirrorsTest {
    private val sha = "a".repeat(64)
    private val ms = StoreMirrors.MODELSCOPE_STORE
    private val raw = "https://raw.githubusercontent.com/Cyletix/CyIME/main/store/"
    private val cdn = "https://cdn.jsdelivr.net/gh/Cyletix/CyIME@main/store/"

    @Test fun chinaReadsTheStoreFromModelScopeFirst() {
        assertEquals(listOf(ms, cdn, raw), StoreMirrors.orderedBases(listOf(cdn, raw, ms), preferDomestic = true))
        assertEquals(listOf(cdn, raw, ms), StoreMirrors.orderedBases(listOf(ms, cdn, raw), preferDomestic = false))
    }

    @Test fun ownPluginPackageHasEveryStoreRoute() {
        val url = raw + "plugins/packages/typing-stats-0.3.1.xipk"
        val china = StoreMirrors.fileCandidates(url, sha, listOf(ms, cdn, raw), preferDomestic = true)
        assertEquals(ms + "plugins/packages/typing-stats-0.3.1.xipk", china.first())
        assertTrue(cdn + "plugins/packages/typing-stats-0.3.1.xipk" in china)
        assertTrue(url in china)
        assertEquals(url, StoreMirrors.fileCandidates(url, sha, listOf(ms, cdn, raw), preferDomestic = false).first())
    }

    @Test fun thirdPartyFileGetsModelScopeCopyAndJsDelivr() {
        val url = "https://raw.githubusercontent.com/rime/rime-wubi/152a0d3/wubi86.dict.yaml"
        val china = StoreMirrors.fileCandidates(url, sha, emptyList(), preferDomestic = true)
        assertEquals(ms + "mirror/raw.githubusercontent.com/rime/rime-wubi/152a0d3/wubi86.dict.yaml", china.first())
        assertTrue("https://cdn.jsdelivr.net/gh/rime/rime-wubi@152a0d3/wubi86.dict.yaml" in china)
        val archive = "https://github.com/cz-archive/rime-wubi98/archive/refs/tags/1.0.0.tar.gz"
        assertEquals(ms + "mirror/github.com/cz-archive/rime-wubi98/archive/refs/tags/1.0.0.tar.gz",
            StoreMirrors.fileCandidates(archive, sha, emptyList(), preferDomestic = true).first())
    }

    /** 换线路必须能校验：没有 sha256 的文件只走原地址。 */
    @Test fun unverifiedFilesNeverSwitchRoute() {
        val url = raw + "rimes/x.zip"
        assertEquals(listOf(url), StoreMirrors.fileCandidates(url, null, listOf(ms), preferDomestic = true))
        assertEquals(listOf(url), StoreMirrors.fileCandidates(url, "", listOf(ms), preferDomestic = true))
    }

    @Test fun modelScopeIsTheDomesticRoute() {
        assertTrue(DownloadMirrors.isDomestic(ms))
        assertEquals(null, StoreMirrors.mirrorPath("https://example.com/a.zip"))
        assertEquals(null, StoreMirrors.mirrorPath("https://github.com/../etc"))
    }
}
