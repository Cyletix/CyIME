package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 导入的方案包在「导入和下载的方案包」里用压缩包名显示，不显示 import_日期 目录名。 */
class ImportManagerTest {
    @Test
    fun archiveTitleDropsArchiveExtension() {
        assertEquals("rime-ice-main", ImportManager.archiveTitle("rime-ice-main.zip"))
        assertEquals("rime-wubi98-1.0.0", ImportManager.archiveTitle("rime-wubi98-1.0.0.tar.gz"))
        assertEquals("my", ImportManager.archiveTitle("my.TGZ"))
    }

    @Test
    fun archiveTitleKeepsOtherNames() {
        assertEquals("wubi86.schema.yaml", ImportManager.archiveTitle("wubi86.schema.yaml"))
        assertEquals(".zip", ImportManager.archiveTitle(".zip"))
    }
}
