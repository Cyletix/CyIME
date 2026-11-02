package com.kingzcheung.xime.stats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.IncognitoState
import com.kingzcheung.xime.stats.TypingStatsData.Combo
import com.kingzcheung.xime.stats.TypingStatsData.Source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** 按键、点选候选发生在上屏之前：记到紧接着上屏的那种输入方式上。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class TypingStatsAttributionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val t9 = TypingStatsCombo("kb/zh/pinyin/t9", "中文 · 拼音 · 九键")
    private val qwerty = TypingStatsCombo("kb/zh/pinyin/qwerty", "中文 · 拼音 · 26键")

    @Before fun setUp() {
        // 连续打字看「上一次上屏时刻 > 0」，先把时钟拨离 0。
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        TypingStats.init(app); TypingStats.setEnabled(app, true); TypingStats.clear(app)
    }
    @After fun tearDown() { IncognitoState.manual = false; TypingStats.clear(app) }

    @Test
    fun keysPicksAndSpeedGoToTheComboThatCommits() {
        repeat(4) { TypingStats.onKeyDown("4") }
        TypingStats.onKeyDown("delete")
        TypingStats.onCandidatePicked(0, "")
        TypingStats.onCommit("你好", isPaste = false, source = Source.T9, combo = t9)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        repeat(3) { TypingStats.onKeyDown("q") }
        TypingStats.onCandidatePicked(1, "")
        TypingStats.onCommit("去", isPaste = false, source = Source.PINYIN, combo = qwerty)

        val s = TypingStats.snapshot(app)
        val a = s.combos.getValue(t9.id)
        assertEquals(2, a.chars)
        assertEquals(5, a.counts[Combo.KEYS])
        assertEquals(1, a.counts[Combo.DELETES])
        assertEquals(1, a.counts[Combo.PICKS])
        assertEquals(1, a.counts[Combo.FIRST_PICKS])
        assertEquals(1, a.days)
        val b = s.combos.getValue(qwerty.id)
        assertEquals(1, b.chars)
        assertEquals(3, b.counts[Combo.KEYS])
        assertEquals(0, b.counts[Combo.FIRST_PICKS])
        // 两次上屏隔 2 秒：这 2 秒和第二次的字记在第二种输入方式上
        assertEquals(2_000, b.counts[Combo.ACTIVE_MS])
        assertEquals(1, b.counts[Combo.ACTIVE_CHARS])
        assertEquals(0, a.counts[Combo.ACTIVE_MS])
    }

    @Test
    fun pasteDropsPendingKeysAndIncognitoRecordsNothing() {
        repeat(3) { TypingStats.onKeyDown("a") }
        TypingStats.onCommit("粘贴的一段", isPaste = true, source = Source.PINYIN, combo = qwerty)
        TypingStats.onCommit("好", isPaste = false, source = Source.PINYIN, combo = qwerty)
        assertEquals(0, TypingStats.snapshot(app).combos.getValue(qwerty.id).counts[Combo.KEYS])

        IncognitoState.manual = true
        TypingStats.onKeyDown("a")
        TypingStats.onCommit("秘密", isPaste = false, source = Source.T9, combo = t9)
        assertNull(TypingStats.snapshot(app).combos[t9.id])
    }

    @Test
    fun voiceWithoutComboIsCountedAsVoiceButNotSpeed() {
        TypingStats.onCommit("今天天气不错", isPaste = false, source = Source.VOICE)
        val v = TypingStats.snapshot(app).combos.getValue(TypingStatsCombo.VOICE.id)
        assertEquals(6, v.chars)
        assertNull(v.speed())
        assertEquals(0, v.counts[Combo.KEY_CHARS])
    }
}
