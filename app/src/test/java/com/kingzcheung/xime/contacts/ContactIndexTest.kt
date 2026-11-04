package com.kingzcheung.xime.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactIndexTest {
    /** 同 assets/handwriting/hanzi_pinyin.tsv 的写法：带声调，多音字用 / 隔开。 */
    private val table = mapOf(
        '张' to "zhāng", '三' to "sān", '曾' to "céng/zēng", '小' to "xiǎo", '明' to "míng",
        '欧' to "ōu", '阳' to "yáng", '乐' to "lè/yuè", '吕' to "lǚ", '布' to "bù", '王' to "wáng", '伟' to "wěi",
    )
    private fun readings(cp: Int): String? = table[cp.toChar()]

    private fun index(vararg contacts: Pair<String, List<String>>) =
        ContactIndex.build(contacts.map { ContactIndex.RawContact(it.first, it.second) }, ::readings)

    @Test fun fullPinyinAndT9DigitsFindTheName() {
        val idx = index("张三" to listOf("+86 138-0013-8000"))
        val hit = idx.lookup("zhangsan", null).single()
        assertEquals("张三", hit.name)
        assertEquals(listOf("13800138000"), hit.phones)
        assertEquals(hit, idx.lookup(null, "94264726").single())
        assertTrue(idx.lookup("zhangsa", null).isEmpty())
        assertTrue(idx.lookup("zs", null).isEmpty())
    }

    @Test fun surnameReadingComesFirstButDictionaryReadingsStay() {
        val idx = index("曾小明" to emptyList(), "乐乐" to emptyList())
        assertEquals("曾小明", idx.lookup("zengxiaoming", null).single().name)
        assertEquals("曾小明", idx.lookup("cengxiaoming", null).single().name)
        assertEquals("乐乐", idx.lookup("lele", null).single().name)
        assertEquals("乐乐", idx.lookup("yueyue", null).single().name)
    }

    @Test fun initialsOnlyForThreeCharactersOrMore() {
        val idx = index("欧阳小明" to emptyList(), "王伟" to emptyList())
        assertEquals("欧阳小明", idx.lookup("oyxm", null).single().name)
        assertEquals("欧阳小明", idx.lookup(null, ContactIndex.toT9("oyxm")).single().name)
        assertTrue(idx.lookup("ww", null).isEmpty())
        assertEquals("王伟", idx.lookup("wangwei", null).single().name)
    }

    @Test fun umlautIsTypedAsV() {
        assertEquals("吕布", index("吕布" to emptyList()).lookup("lvbu", null).single().name)
    }

    @Test fun bracketNotesAreDroppedAndOddNamesSkipped() {
        val idx = index("张三（公司）" to emptyList(), "妈" to emptyList(), "张3" to emptyList(), "Tom Lee" to emptyList())
        assertEquals("张三", idx.lookup("zhangsan", null).single().name)
        assertEquals("Tom Lee", idx.lookup("tomlee", null).single().name)
        // 一个字的、夹数字的名字不能按拼音找，但仍算联系人（剪贴板照样挡）。
        assertTrue(idx.lookup("ma", null).isEmpty())
        assertTrue(idx.containsText("妈"))
        assertEquals(4, idx.size)
    }

    @Test fun phoneNormalization() {
        assertEquals("13800138000", ContactIndex.normalizePhone("+86 138 0013 8000"))
        assertEquals("13800138000", ContactIndex.normalizePhone("0086-13800138000"))
        assertEquals("+12025550123", ContactIndex.normalizePhone("+1 (202) 555-0123"))
        assertEquals("0101234", ContactIndex.normalizePhone("010-1234"))
        assertNull(ContactIndex.normalizePhone("abc123"))
        assertNull(ContactIndex.normalizePhone("12"))
    }

    @Test fun containsTextMatchesNamesAndNumbersOnly() {
        val idx = index("张三" to listOf("13800138000"))
        assertTrue(idx.containsText(" 张三 "))
        assertTrue(idx.containsText("+86 138-0013-8000"))
        assertFalse(idx.containsText("张三你好"))
        assertFalse(idx.containsText("13800138001"))
    }

    @Test fun atMostThreePeopleAndTwoNumbersEach() {
        val idx = index(
            "张三" to listOf("1", "13800000001", "13800000002", "13800000003"),
            "张叁" to emptyList(),
        )
        // 「张叁」的「叁」不在测试读音表里：查不到拼音，不出候选。
        assertEquals(2, idx.size)
        assertEquals(1, idx.lookup("zhangsan", null).size)
        assertEquals(listOf("13800000001", "13800000002"), idx.lookup("zhangsan", null).single().phones)
    }

    @Test fun codesWithHanOrDigitsAreNotLookedUp() {
        assertEquals("zhangsan", ContactIndex.lettersOf("zhang'san"))
        assertEquals("zhangsan", ContactIndex.lettersOf("zhang san"))
        assertNull(ContactIndex.lettersOf("张san"))
        assertNull(ContactIndex.lettersOf("zhang3"))
        assertEquals("94264726", ContactIndex.digitsOf("94264'726"))
        assertNull(ContactIndex.digitsOf("9426a"))
        assertNull(ContactIndex.digitsOf("1234"))
    }
}
