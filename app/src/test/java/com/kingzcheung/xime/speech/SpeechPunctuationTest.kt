package com.kingzcheung.xime.speech

import com.kingzcheung.xime.service.normalizeVoiceText
import com.kingzcheung.xime.settings.InputLanguage
import org.junit.Assert.*
import org.junit.Test

/** 整句定稿 keeps the model's sentence and clause marks; the 6 s mode keeps turning them into spaces. */
class SpeechPunctuationTest {
    @Test fun `kept marks become full width after Chinese and survive repeated cleanup`() {
        val once = cleanSenseVoiceText("<|zh|><|NEUTRAL|><|Speech|><|withitn|>今天天气不错, 我们出去走走吧.", keepMarks = true)
        assertEquals("今天天气不错，我们出去走走吧。", once)
        assertEquals(once, cleanSpeechText(once, keepMarks = true))
        assertEquals("今天天气不错 我们出去走走吧", cleanSpeechText(once)) // switch off: old spacing
    }

    @Test fun `number notation is not mistaken for sentence marks`() {
        assertEquals("体温36.5度，比昨天高了20%，会议10:30开始，花了1,200元。",
            cleanSpeechText("体温36.5度，比昨天高了20%，会议10:30开始，花了1,200元。", keepMarks = true))
        assertEquals("版本是1.14。", cleanSpeechText("版本是1.14.", keepMarks = true))
    }

    @Test fun `mixed Chinese and English keep ASCII marks after Latin words`() {
        assertEquals("我用 iPhone, 很好用。", cleanSpeechText("我用 iPhone，很好用。", keepMarks = true))
        assertEquals("这个 bug 太严重了！", cleanSpeechText("这个 bug 太严重了!", keepMarks = true))
    }

    @Test fun `a run of marks collapses to the strongest one`() {
        assertEquals("真的吗？", cleanSpeechText("真的吗，？", keepMarks = true))
        assertEquals("好的。", cleanSpeechText("。好的。。", keepMarks = true))
    }

    @Test fun `dictated punctuation replaces the model marks around it`() {
        assertEquals("你好，我在这里。",
            normalizeVoiceText("你好，逗号，我在这里。", InputLanguage.CHINESE, keepMarks = true))
        assertEquals("你吃饭了吗？", normalizeVoiceText("你吃饭了吗问号。", InputLanguage.CHINESE, keepMarks = true))
        assertEquals("你好，我在", normalizeVoiceText("你好逗号我在")) // switch off: dictated marks still work
        assertEquals("你好 我在", normalizeVoiceText("你好，我在。")) // switch off: model marks become spacing
    }

    @Test fun `regions never repeat a full stop and need no space after a mark`() {
        val t = SpeechTranscript(keepMarks = true)
        t.update(1, "今天天气不错。", SpeechBoundary.PAUSE)
        assertEquals("今天天气不错。我们出去吧。", t.update(2, "。我们出去吧。", SpeechBoundary.PAUSE))
        val preview = SpeechTranscript(keepMarks = true)
        preview.update(1, "第一句。", SpeechBoundary.PAUSE)
        assertEquals("第一句。第二句还在说", preview.update(2, "第二句还在说")) // streaming preview has no marks yet
    }

    @Test fun `session keeps marks only with whole sentence on`() {
        class Engine : LocalSpeechEngine {
            override val hasRefinement = true
            override val isStreaming = true
            override fun detector(): SpeechDetector = object : SpeechDetector {
                override fun speech(frame: FloatArray) = frame.any { it != 0f }
                override fun reset() { }
            }
            override fun accept(samples: FloatArray) = "预览"
            override fun finishStream() = "预览"
            override fun refine(samples: FloatArray) = cleanSenseVoiceText("你好，世界。")
            override fun refinePunctuated(samples: FloatArray) = cleanSenseVoiceText("你好，世界。", keepMarks = true)
            override fun reset() { }
            override fun close() { }
        }
        val pcm = ByteArray(32000) { if (it % 2 == 0) 64 else 6 }
        assertEquals("你好 世界", LocalSpeechSession(Engine(), {}, {}).apply { acceptPcm(pcm) }.finish())
        assertEquals("你好，世界。", LocalSpeechSession(Engine(), {}, {}, wholeSentence = true).apply { acceptPcm(pcm) }.finish())
    }
}
