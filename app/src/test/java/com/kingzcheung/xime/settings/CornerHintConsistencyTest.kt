package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * 所有字母布局的角标只有两条规则（用户 10-30：模式太多难免疏忽，统一）：
 * 单字母键按所在位置用 26 键那一格的上下角标（换排列、自建布局都一样，蜂窝按字母）；
 * 合并键（14/17/18 键、自建双字母布局）按读顺序用 [MergedKeySymbols]。
 * 每个字母键上下两个角标都有，印的就是上屏的（label == value）。手势方向见 CornerHintGestureTest。
 */
class CornerHintConsistencyTest {
    @get:Rule val temp = TemporaryFolder()
    private val defaults get() = requireNotNull(javaClass.classLoader?.getResource("xime.yaml")).readText()
    private val merged = "custom_pinyin_" + "c".repeat(32)
    private lateinit var context: android.content.Context

    @Before fun load() {
        val assets = mock<android.content.res.AssetManager>()
        whenever(assets.open("xime.yaml")).thenAnswer { defaults.byteInputStream() }
        whenever(assets.open("xime.custom.yaml")).thenThrow(java.io.IOException("No custom override"))
        context = mock<android.content.Context>()
        whenever(context.assets).thenReturn(assets)
        whenever(context.filesDir).thenReturn(temp.root)
        whenever(context.getSharedPreferences("kime_settings", android.content.Context.MODE_PRIVATE))
            .thenReturn(mock<android.content.SharedPreferences>())
        // 一个自建的双字母布局：qw、as 两个合并键，其余单字母。
        File(temp.root, "custom-keyboard-layouts.json").writeText("""[{"formatVersion":2,"id":"$merged","name":"我的合并",""" +
            """"rows":"qw,e,r,t,y,u,i,o,p/as,d,f,g,h,j,k,l/z,x,c,v,b,n,m"}]""")
        CustomKeyboardLayouts.load(context)
        KeysConfigHelper.loadConfig(context)
    }

    @After fun reset() {
        File(temp.root, "custom-keyboard-layouts.json").writeText("[]")
        CustomKeyboardLayouts.load(context)
        LetterLayouts.setForTest(InputLanguage.ENGLISH, null)
        KeysConfigHelper.setActiveKeyboardSchema("")
        KeysConfigHelper.applyLetterLayouts()
    }

    private data class Board(val schema: String, val english: Boolean = false, val englishArrangement: String? = null)

    private val singleLetterBoards = listOf(Board("rime_ice"), Board("double_pinyin_flypy"), Board("rime_ice", english = true),
        Board(QwjrtkLayout.ID), Board(Cyletix10Layout.ID), Board(ColemakLayout.ID), Board(TypewiseLayout.ID),
        Board("rime_ice", english = true, englishArrangement = Cyletix10Layout.ID),
        Board("rime_ice", english = true, englishArrangement = ColemakLayout.ID),
        Board("rime_ice", english = true, englishArrangement = TypewiseLayout.ID))
    private val mergedBoards = listOf(Board("pinyin_14jian"), Board("double_pinyin_flypy_14jian"),
        Board("pinyin_17jian"), Board("pinyin_18jian"), Board(merged))

    private val pairs = listOf("（" to "）", "《" to "》", "【" to "】", "「" to "」", "『" to "』", "[" to "]", "<" to ">")

    private fun use(board: Board) {
        LetterLayouts.setForTest(InputLanguage.ENGLISH, board.englishArrangement)
        KeysConfigHelper.setActiveKeyboardSchema(board.schema)
        KeysConfigHelper.applyLetterLayouts()
    }

    private fun letterKeys(board: Board) = KeysConfigHelper.getKeyRows(board.english).flatten()
        .filter { key -> key.isNotEmpty() && key.all { it.lowercaseChar() in 'a'..'z' } }.map { it.lowercase() }

    /** 上下两个角标都有，都是直接上屏，印的就是上屏的。 */
    private fun hints(board: Board, key: String): Pair<String, String> {
        val gesture = KeysConfigHelper.getKeyGesture(key, board.english)
        val up = gesture?.swipeUp
        val down = gesture?.swipeDown
        assertNotNull("$board $key 没有上角标", up)
        assertNotNull("$board $key 没有下角标", down)
        for ((name, def) in listOf("上" to up!!, "下" to down!!)) {
            assertTrue("$board $key $name 角标是空的", def.value.isNotEmpty())
            assertEquals("$board $key $name 角标印的和上屏的不一样", def.value, def.label)
            assertEquals("$board $key $name 角标不是直接上屏", GestureAction.COMMIT, def.action)
            assertNotEquals("$board $key $name 角标只在滑动气泡里显示", DisplayMode.BUBBLE, def.display)
        }
        return up.value to down.value
    }

    @Test fun singleLetterKeysTakeTheSymbolsOfTheirPosition() {
        for (board in singleLetterBoards) {
            use(board)
            val arrangement = KeysConfigHelper.letterLayout(board.schema, board.english)
            val keys = letterKeys(board)
            assertEquals("$board 26 个字母", 26, keys.size)
            for (key in keys) {
                val (up, down) = hints(board, key)
                val position = arrangement?.positionLetter(key) ?: key
                assertEquals("$board $key 上角标是 26 键 $position 那一格的", QwertySwipeSymbols.up(board.english).getValue(position), up)
                assertEquals("$board $key 下角标是 26 键 $position 那一格的", QwertySwipeSymbols.down(board.english).getValue(position), down)
            }
            // 26 个字母各占 26 键的一格，没有落空也没有两个键同一格；挪进字母区的分号键自己不带角标。
            assertEquals("$board 26 个字母各占一格", 26, keys.map { arrangement?.positionLetter(it) ?: it }.toSet().size)
            if (arrangement?.hasSemicolon() == true) KeysConfigHelper.getKeyGesture(";", board.english).let {
                assertNull("$board 分号键没有上角标", it?.swipeUp)
                assertNull("$board 分号键没有下角标", it?.swipeDown)
            }
            // 第一排第一个键无论是什么字母，都是 1。
            if (arrangement?.honeycomb != true) assertEquals("$board 左上角是 1", "1", hints(board, keys.first()).first)
        }
    }

    @Test fun cyletix10LetterPushedOutBySemicolonTakesTheSemicolonsSlot() {
        // Cyletix10 第一排的分号键站在 26 键 y 那一格，u 被挤到第二排末尾多出来的一格：u 拿 y 那一格（6）。
        for (english in listOf(false, true)) {
            use(if (english) Board("rime_ice", true, Cyletix10Layout.ID) else Board(Cyletix10Layout.ID))
            assertEquals("y", KeysConfigHelper.letterLayout(if (english) "rime_ice" else Cyletix10Layout.ID, english)!!.positionLetter("u"))
            assertEquals("6", KeysConfigHelper.getKeyGesture("u", english)!!.swipeUp!!.value)
        }
    }

    @Test fun mergedKeysFollowOneTableInReadingOrder() {
        for (board in mergedBoards) {
            use(board)
            val keys = letterKeys(board)
            val ups = keys.map { hints(board, it).first }
            val downs = keys.map { hints(board, it).second }
            assertEquals("$board 前 10 个键上角标 1-0", "1234567890", ups.take(10).joinToString(""))
            assertEquals("$board 上角标按共用表", keys.map { MergedKeySymbols.up(keys).getValue(it) }, ups)
            assertEquals("$board 下角标按共用表", keys.map { MergedKeySymbols.down(keys).getValue(it) }, downs)
            assertEquals("$board 角标不重复", (ups + downs).size, (ups + downs).toSet().size)
            if (board.schema != merged) for ((open, close) in pairs) for (side in listOf(ups, downs))
                assertEquals("$board $open$close 成对出现", open in side, close in side)
            // Shift 拆开的单字母沿用所在合并键的角标。
            for (key in keys.filter { it.length == 2 }) for (letter in key.map(Char::toString)) {
                val single = KeysConfigHelper.getKeyGesture(letter, false)!!
                assertEquals("$board $letter 上", MergedKeySymbols.up(keys).getValue(key), single.swipeUp!!.value)
                assertEquals("$board $letter 下", MergedKeySymbols.down(keys).getValue(key), single.swipeDown!!.value)
            }
        }
    }

    @Test fun nineKeyAndStrokeShowOnlyTheirDigitAndBindNothingBelow() {
        // 九键、笔画的键面已经印着字母/笔画，角上只放数字（上角标）；没有下角标，也就没有能滑出看不见的东西的下滑。
        for (digit in 1..9) {
            val t9 = KeysConfigHelper.getT9KeyGesture(digit.toString())!!
            assertEquals(digit.toString(), t9.swipeUp!!.value)
            assertNull("九键 $digit 没有下角标", t9.swipeDown)
        }
        for ((index, key) in listOf("一", "丨", "丿", "丶", "乛", "*", "分词", "，", "英").withIndex()) {
            val stroke = KeysConfigHelper.getStrokeKeyGesture(key)!!
            assertEquals((index + 1).toString(), stroke.swipeUp!!.value)
            assertNull("笔画 $key 没有下角标", stroke.swipeDown)
        }
    }
}
