package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AssociationManager {
    private const val TAG = "AssociationManager"
    
    @Volatile
    private var isInitialized = false
    private val mutex = Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    
    private lateinit var fusionEngine: NgramFusionEngine
    private var context: Context? = null
    private var learningCache: UserNgramCache? = null
    private fun learningCache(ctx: Context): UserNgramCache = synchronized(this) {
        learningCache ?: UserNgramCache(ctx.applicationContext).also { learningCache = it }
    }
    
    suspend fun initialize(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        context = ctx.applicationContext
        if (isInitialized) {
            FileLogger.d(TAG, "Already initialized")
            return@withContext true
        }
        
        mutex.withLock {
            val token = generation.get()
            if (isInitialized) {
                FileLogger.d(TAG, "Already initialized (in lock)")
                return@withContext true
            }
            
            try {
                fusionEngine = NgramFusionEngine(ctx, learningCache(ctx))
                val cacheInit = fusionEngine.initialize()
                FileLogger.i(TAG, "NgramFusionEngine init result: $cacheInit")

                if (generation.get() != token) return@withContext false

                isInitialized = cacheInit
                isInitialized

            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize AssociationManager: ${e.message}", e)
                false
            }
        }
    }
    
    /** [afterWholeWord]: the last commit was a phrase of two or more characters; see [BaseAssociationModel.predict]. */
    suspend fun predict(contextText: String, topK: Int = 20, afterWholeWord: Boolean = false): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            val ctx = context
            if (ctx != null) {
                val initSuccess = withContext(Dispatchers.IO) {
                    initialize(ctx)
                }
                if (!initSuccess) {
                    Log.e(TAG, "Initialization failed, returning empty list")
                    return@withContext emptyList()
                }
            } else {
                Log.e(TAG, "Context is null, cannot initialize")
                return@withContext emptyList()
            }
        }
        
        // 联想只用内置 n-gram 联想表和个人学习（11-02 起不再用神经联想模型：离线对比几乎不加分，
        // 却要单独下载、占内存、键盘收起后重载）。
        try {
            val fusedCandidates = fusionEngine.fuseCandidates(contextText, afterWholeWord)
            
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
        if (!isInitialized) return
        fusionEngine.recordUserInput(text)
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

    suspend fun importLearningData(ctx: Context, data: PersonalLearningData) {
        val cache = learningCache(ctx)
        cache.initialize()
        cache.importData(data)
    }
    
    fun getCacheSize(): Int {
        return if (isInitialized) fusionEngine.getCacheSize() else 0
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun release() {
        generation.incrementAndGet()
        isInitialized = false
        context = null
    }

}
