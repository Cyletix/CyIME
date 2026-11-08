package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AssociationManager {
    private const val TAG = "AssociationManager"
    
    @Volatile
    private var fusionEngine: NgramFusionEngine? = null
    private val mutex = Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    
    private var learningCache: UserNgramCache? = null
    private fun learningCache(ctx: Context): UserNgramCache = synchronized(this) {
        learningCache ?: UserNgramCache(ctx.applicationContext).also { learningCache = it }
    }
    
    suspend fun initialize(ctx: Context): Boolean {
        val token = generation.get()
        if (fusionEngine != null) return true
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (token != generation.get()) return@withLock false
                if (fusionEngine != null) return@withLock true
                if (!SettingsPreferences.isSmartPredictionEnabled(ctx)) return@withLock false
                try {
                    val engine = NgramFusionEngine(ctx.applicationContext, learningCache(ctx))
                    if (!engine.initialize()) return@withLock false
                    synchronized(this@AssociationManager) {
                        if (token != generation.get()) return@withLock false
                        fusionEngine = engine
                    }
                    true
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (e: Exception) {
                    FileLogger.e(TAG, "Failed to initialize AssociationManager: ${e.message}", e)
                    false
                }
            }
        }
    }
    
    /** [afterWholeWord]: the last commit was a phrase of two or more characters; see [BaseAssociationModel.predict]. */
    suspend fun predict(contextText: String, topK: Int = 20, afterWholeWord: Boolean = false): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        val engine = fusionEngine ?: mutex.withLock { fusionEngine } ?: return@withContext emptyList()
        
        // 联想只用内置 n-gram 联想表和个人学习（11-02 起不再用神经联想模型：离线对比几乎不加分，
        // 却要单独下载、占内存、键盘收起后重载）。
        try {
            val fusedCandidates = engine.fuseCandidates(contextText, afterWholeWord)
            
            usableCandidates(fusedCandidates).take(topK)
            
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    /**
     * 去掉不能当联想词的候选：模型在英文字母后会“续写”单个字母（e → n s r i…）；学过的句尾标点会被当成
     * 下一个“词”预测出来（打完“吗”总冒出 ？" ），只有标点和空白的候选一律不出（表情照常）。
     */
    internal fun usableCandidates(candidates: List<AssociationCandidate>): List<AssociationCandidate> =
        candidates.filterNot { candidate ->
            val text = candidate.text
            (text.length == 1 && text[0].let { c -> c in 'a'..'z' || c in 'A'..'Z' }) ||
                text.codePoints().allMatch { Character.isWhitespace(it) || isPunctuation(it) }
        }

    private fun isPunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION -> true
        else -> false
    }

    fun recordInput(text: String) {
        fusionEngine?.recordUserInput(text)
    }
    
    /** Keyboard hidden: save typed observations now (typing saves at most every 30 s). */
    fun flushLearning() {
        synchronized(this) { learningCache }?.flush()
    }

    suspend fun saveUserData() {
        checkNotNull(learningCache) { "学习数据尚未加载" }.save()
    }

    suspend fun learningData(ctx: Context): PersonalLearningData {
        val cache = learningCache(ctx)
        cache.initialize()
        return cache.snapshot()
    }

    suspend fun importLearningData(ctx: Context, data: PersonalLearningData, merge: Boolean) {
        val cache = learningCache(ctx)
        cache.initialize()
        cache.importData(data, merge)
    }
    
    fun getCacheSize(): Int = fusionEngine?.getCacheSize() ?: 0
    
    fun isInitialized(): Boolean = fusionEngine != null
    
    @Synchronized fun release() {
        generation.incrementAndGet()
        fusionEngine = null
    }

}
