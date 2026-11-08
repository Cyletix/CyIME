package com.kingzcheung.xime.gloss

import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class InstalledGlossCorrectionsTest {
    @Test fun installedCorrectionsOverridePublishedV12SoftwareTerms() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val corrections = CandidateGlossary.readCorrections(resources.openRawResource(R.raw.candidate_gloss_corrections))
        val v12 = ByteBuffer.wrap("download\t卸载\n卸载\tunload cargo\n".toByteArray())
        val table = GlossTable.fromBuffer(v12, corrections)
        assertEquals(listOf("下载", "卸载", "更新"), CandidateGlossary.annotate(table,
            listOf("Download", "uninstall", "update"), emptyList()))
        assertEquals("uninstall", CandidateGlossary.englishFor(table, "卸载"))
    }
}
