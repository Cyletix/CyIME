package com.kingzcheung.xime.association

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UserNgramCacheSaveTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val file get() = File(app.filesDir, "user_ngram_cache.json")

    @Before fun clean() { file.delete() }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return true; Thread.sleep(20) }
        return condition()
    }

    @Test fun typingDoesNotRewriteTheFileAfterEveryPause() {
        val cache = UserNgramCache(app)
        runBlocking { cache.initialize() }
        cache.saveDelayMs = 60_000L
        cache.recordInput("你好世界")
        Thread.sleep(800) // the old code wrote 500 ms after each commit
        cache.recordInput("你好")
        Thread.sleep(800)
        assertFalse(file.exists())
    }

    @Test fun hidingTheKeyboardSavesAtOnce() {
        val cache = UserNgramCache(app)
        runBlocking { cache.initialize() }
        cache.saveDelayMs = 60_000L
        cache.recordInput("你好世界")
        cache.flush()
        assertTrue(waitFor(3_000) { file.exists() })
        val saved = PersonalLearningData.decode(file.readText(), storedLocally = true)
        assertTrue(saved.bigrams.any { it.tokens == listOf("你", "好") })
    }

    @Test fun typingStillSavesWithinTheDelayWhileTheKeyboardStaysOpen() {
        val cache = UserNgramCache(app)
        runBlocking { cache.initialize() }
        cache.saveDelayMs = 200L
        cache.recordInput("你好世界")
        cache.recordInput("世界")
        assertTrue(waitFor(3_000) { file.exists() })
        assertEquals(2, PersonalLearningData.decode(file.readText(), storedLocally = true)
            .bigrams.single { it.tokens == listOf("世", "界") }.count)
    }

    @Test fun nothingLearnedMeansNothingWrittenOnHide() {
        val cache = UserNgramCache(app)
        runBlocking { cache.initialize() }
        cache.flush()
        Thread.sleep(300)
        assertFalse(file.exists())
    }

    @Test fun referenceIsWrittenInTheSameEncodeAsBefore() {
        val data = PersonalLearningData(bigrams = listOf(LearnedSequence(listOf("你", "好"), 3)), recentInputs = listOf("你"))
        val ref = "a".repeat(64)
        val before = JSONObject(data.encode()).put("profileReference", ref)
        // Android 的 org.json 没有 similar()：转成 Map/List 比内容。
        assertEquals(plain(before), plain(JSONObject(data.encode(ref))))
        assertFalse(JSONObject(data.encode()).has("profileReference"))
    }
    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is org.json.JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }
}
