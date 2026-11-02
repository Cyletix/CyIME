package com.kingzcheung.xime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 候选栏剪贴板：带链接的给干净链接、B 站给 BV 号（用户 11-01），原文照旧可选。 */
class ClipboardLinkChoicesTest {
    private fun choices(text: String) = ClipboardLinkChoices.linkChoices(text)

    @Test fun bilibiliShareKeepsOnlyPartAndTime() {
        val web = "【CyIME 输入法演示-哔哩哔哩】 https://www.bilibili.com/video/BV1GJ411x7h7/?share_source=copy_web&vd_source=0a1b2c3d&p=2&t=95"
        assertEquals(listOf("https://www.bilibili.com/video/BV1GJ411x7h7/?p=2&t=95", "BV1GJ411x7h7"), choices(web))
        // 只有链接、后面一串跟踪参数：给干净链接和 BV 号。
        assertEquals(listOf("https://www.bilibili.com/video/BV1GJ411x7h7", "BV1GJ411x7h7"),
            choices("https://www.bilibili.com/video/BV1GJ411x7h7?spm_id_from=333.1007.tianma.1-1-1.click&vd_source=abc"))
        // 手机网页版同样认。
        assertEquals("BV1GJ411x7h7", ClipboardLinkChoices.bilibiliVideoId("https://m.bilibili.com/video/BV1GJ411x7h7?p=3"))
    }

    @Test fun shortLinkIsGivenWithoutTheTitleButHasNoBv() {
        // b23.tv 短链接不联网展不开：只给链接本身，没有 BV 号。
        assertEquals(listOf("https://b23.tv/AbCdEf1"), choices("【标题-哔哩哔哩】 https://b23.tv/AbCdEf1"))
        assertEquals(listOf("b23.tv/AbCdEf1"), choices("看这个 b23.tv/AbCdEf1 哈哈"))
    }

    @Test fun otherSitesLoseTrackingButKeepRealParameters() {
        assertEquals("https://example.com/a?id=7#top",
            ClipboardLinkChoices.cleanLink("https://example.com/a?utm_source=x&id=7&fbclid=abc#top"))
        // 微信文章的 mid、sn 是打开文章要用的，不能当跟踪参数去掉。
        val wx = "https://mp.weixin.qq.com/s?__biz=MzA&mid=2650&idx=1&sn=abc"
        assertEquals(wx, ClipboardLinkChoices.cleanLink(wx))
        // YouTube 的 si 是分享来源，去掉；别的网站的 si 不动。
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", ClipboardLinkChoices.cleanLink("https://youtu.be/dQw4w9WgXcQ?si=Xyz&t=42"))
        assertEquals("https://example.com/?si=1", ClipboardLinkChoices.cleanLink("https://example.com/?si=1"))
    }

    @Test fun nothingExtraWhenTheCopyIsAlreadyACleanLinkOrHasNoLink() {
        assertEquals(emptyList<String>(), choices("https://example.com/page"))
        assertEquals(emptyList<String>(), choices("明天下午三点开会"))
        assertNull(ClipboardLinkChoices.bilibiliVideoId("https://example.com/video/BV1GJ411x7h7"))
        // 句末标点不算链接。
        assertEquals(listOf("https://example.com/page"), choices("地址是 https://example.com/page。"))
        assertEquals(listOf("https://example.com/page"), choices("see https://example.com/page."))
    }

    @Test fun expandedBarPutsTheCleanLinkFirstAndRemembersTheSourceRecord() {
        val texts = listOf("123456", "【标题】 https://www.bilibili.com/video/BV1GJ411x7h7/?vd_source=a")
        assertEquals(
            listOf(
                ClipboardLinkChoices.Choice(0, "123456"),
                ClipboardLinkChoices.Choice(1, "https://www.bilibili.com/video/BV1GJ411x7h7/"),
                ClipboardLinkChoices.Choice(1, "BV1GJ411x7h7"),
                ClipboardLinkChoices.Choice(1, texts[1]),
            ),
            ClipboardLinkChoices.expand(texts))
    }
}
