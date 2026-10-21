package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 文件管理导出：存「下载」失败时不报“导出完成”，备份包留在应用里能分享。 */
class RimeExportSettleTest {
    @get:Rule val temp = TemporaryFolder()

    private fun archive(): File = File(temp.newFolder("cache"), "CyIME配置-2026-10-22.zip").apply { writeText("zip") }

    @Test fun `saved to downloads removes the temporary archive`() {
        val zip = archive()
        val keep = File(temp.root, "exports")
        assertNull(RimeExportManager.settleTempArchive(zip, saved = true, keepDir = keep))
        assertFalse(zip.exists())
        assertFalse(File(keep, zip.name).exists())
    }

    @Test fun `a failed save is reported and the archive is kept for sharing`() {
        val zip = archive()
        val keep = File(temp.root, "exports")
        val failure = RimeExportManager.settleTempArchive(zip, saved = false, keepDir = keep)
        assertTrue(failure != null)
        assertEquals(File(keep, zip.name), failure!!.kept)
        assertEquals("zip", failure.kept.readText())
        assertFalse(zip.exists())
    }
}
