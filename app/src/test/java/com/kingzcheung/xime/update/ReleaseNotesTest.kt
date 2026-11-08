package com.kingzcheung.xime.update

import com.kingzcheung.xime.update.ReleaseNoteMarkdown.Bullet
import com.kingzcheung.xime.update.ReleaseNoteMarkdown.Heading
import com.kingzcheung.xime.update.ReleaseNoteMarkdown.Link
import com.kingzcheung.xime.update.ReleaseNoteMarkdown.Paragraph
import com.kingzcheung.xime.update.ReleaseNoteMarkdown.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test fun onlyPlainVersionFilesAreNotes() {
        assertEquals("1.15.68", ReleaseNotes.versionOf("1.15.68.md"))
        assertNull(ReleaseNotes.versionOf("1.2.10-enter-editor-pad.md"))
        assertNull(ReleaseNotes.versionOf("cyime-0.1.0.md"))
        assertNull(ReleaseNotes.versionOf("packaging.md"))
    }

    @Test fun newestFirstComparesNumbersNotText() {
        val sorted = ReleaseNotes.sortNewestFirst(listOf("1.15.6", "1.15.68", "1.14.156", "1.15.17").map { ReleaseNote(it, "x") })
        assertEquals(listOf("1.15.68", "1.15.17", "1.15.6", "1.14.156"), sorted.map { it.version })
    }

    @Test fun pageExpandsEverythingSinceTheLastSeenVersion() {
        val versions = listOf("1.15.68", "1.15.66", "1.15.65", "1.15.63")
        assertEquals(setOf("1.15.68", "1.15.66"), ReleaseNotes.expandedVersions(versions, "1.15.68", "1.15.65"))
        // 没有记录（刚装这个功能的老用户）或已看过：只展开现在这一版。
        assertEquals(setOf("1.15.68"), ReleaseNotes.expandedVersions(versions, "1.15.68", null))
        assertEquals(setOf("1.15.68"), ReleaseNotes.expandedVersions(versions, "1.15.68", "1.15.68"))
        // 包里带了比自己新的说明（不会发生，但不展开）。
        assertEquals(setOf("1.15.66"), ReleaseNotes.expandedVersions(versions, "1.15.66", "1.15.66"))
    }

    @Test fun promptOnlyOncePerUpdateAndNeverOnFreshInstall() {
        assertTrue(ReleaseNotes.shouldPrompt("1.15.66", "1.15.68", hasCurrentNote = true, enabled = true, freshInstall = false))
        // 从没有这个功能的旧版升上来：也提示。
        assertTrue(ReleaseNotes.shouldPrompt(null, "1.15.68", hasCurrentNote = true, enabled = true, freshInstall = false))
        assertFalse(ReleaseNotes.shouldPrompt("1.15.68", "1.15.68", hasCurrentNote = true, enabled = true, freshInstall = false))
        assertFalse(ReleaseNotes.shouldPrompt(null, "1.15.68", hasCurrentNote = true, enabled = true, freshInstall = true))
        assertFalse(ReleaseNotes.shouldPrompt("1.15.66", "1.15.68", hasCurrentNote = false, enabled = true, freshInstall = false))
        assertFalse(ReleaseNotes.shouldPrompt("1.15.66", "1.15.68", hasCurrentNote = true, enabled = false, freshInstall = false))
    }

    @Test fun parsesTheFewMarkdownFormsTheNotesUse() {
        val blocks = ReleaseNoteMarkdown.blocks(
            """
            # CyIME 1.15.66

            从 1.15.63 直接覆盖安装即可，
            词库和设置都会保留。

            **回车发送**

            - 新增「回车发送」开关，默认关闭。
              - 打开后按回车直接发送。
            ## 输入与布局
            * 修复 14 键
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Heading("CyIME 1.15.66", 1),
                Paragraph("从 1.15.63 直接覆盖安装即可，词库和设置都会保留。"),
                Heading("回车发送", 3),
                Bullet("新增「回车发送」开关，默认关闭。", 0),
                Bullet("打开后按回车直接发送。", 1),
                Heading("输入与布局", 2),
                Bullet("修复 14 键", 0),
            ),
            blocks
        )
        assertEquals(Paragraph("从 1.15.63 直接覆盖安装即可，词库和设置都会保留。"), ReleaseNoteMarkdown.withoutTitle(blocks).first())
    }

    @Test fun inlineBoldCodeAndLinks() {
        // 不成对的星号原样留着。
        assertEquals(listOf(Text("**")), ReleaseNoteMarkdown.spans("**"))
        assertEquals(
            listOf(
                Text("轻量版", bold = true),
                Text(" "),
                Text("CyIME-1.15.33-arm64-v8a.apk", code = true),
                Text("，见 "),
                Link("CC0 1.0 中文说明", "https://creativecommons.org/publicdomain/zero/1.0/deed.zh-hans"),
            ),
            ReleaseNoteMarkdown.spans("**轻量版** `CyIME-1.15.33-arm64-v8a.apk`，见 [CC0 1.0 中文说明](https://creativecommons.org/publicdomain/zero/1.0/deed.zh-hans)")
        )
    }

    @Test fun summaryIsTheFirstBodyLineWithoutMarks() {
        assertEquals("新增 上传", ReleaseNoteMarkdown.summary("# CyIME 1.15.68\n\n## 贡献样本\n\n- 新增 **上传**"))
        assertEquals("一二三…", ReleaseNoteMarkdown.summary("一二三四五", maxChars = 3))
    }

    /** 打包时带进去的就是 docs/<x.y.z>.md：每一份都要解析出正文，不能只剩标题。 */
    @Test fun everyBundledNoteHasBody() {
        val docs = java.io.File("../docs")
        val files = docs.listFiles { f -> ReleaseNotes.versionOf(f.name) != null }.orEmpty()
        assertTrue("docs 里没有 x.y.z.md", files.isNotEmpty())
        for (f in files) {
            val body = ReleaseNoteMarkdown.withoutTitle(ReleaseNoteMarkdown.blocks(f.readText()))
            assertTrue("${f.name} 没有正文", body.any { it !is Heading })
        }
    }
}
