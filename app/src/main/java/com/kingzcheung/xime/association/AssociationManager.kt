package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.model.ModelRuntime
import com.kingzcheung.xime.model.ModelManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
                
                ModelRuntime.register(
                    id = "predictive_text",
                    loader = { OnnxAssociationEngine.initialize(ctx.applicationContext) },
                    releaser = { OnnxAssociationEngine.release() },
                    label = "智能联想模型"
                )
                val modelLoaded = ModelManager.isModelReady(ctx, SettingsPreferences.getPredictionSelectedModel(ctx)) &&
                    ModelRuntime.load("predictive_text")
                FileLogger.i(TAG, "ModelRuntime.load(predictive_text) result: $modelLoaded")

                if (generation.get() != token) {
                    OnnxAssociationEngine.release()
                    return@withContext false
                }

                if (modelLoaded) {
                    ModelRuntime.keepWarm("predictive_text")
                    OnnxAssociationEngine.startWarmup()
                }

                val cacheInit = fusionEngine.initialize()
                FileLogger.i(TAG, "NgramFusionEngine init result: $cacheInit")

                if (generation.get() != token) {
                    ModelRuntime.releaseWarm("predictive_text")
                    OnnxAssociationEngine.release()
                    return@withContext false
                }
                
                isInitialized = cacheInit || modelLoaded
                
                FileLogger.i(TAG, "AssociationManager initialized: model=$modelLoaded, cache=$cacheInit")
                isInitialized
                
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize AssociationManager: ${e.message}", e)
                false
            }
        }
    }
    
    suspend fun predict(contextText: String, topK: Int = 20): List<AssociationCandidate> = withContext(Dispatchers.Default) {
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
        
        // :inference 进程被系统回收（空闲回收是设计内行为，同 AsrInferenceService 的
        // 空闲释放）后，OnnxAssociationEngine 检测到失联会重置自身状态，而本类的
        // isInitialized 仍为 true。必须在这里检测引擎状态并按需重载（重新 bind +
        // loadModel），否则所有预测入口都会被本方法上方的 isInitialized 守卫放行、
        // 撞上引擎的 "Engine not initialized" 空转，联想永远失效。
        if (!OnnxAssociationEngine.isInitialized() && context?.let {
                ModelManager.isModelReady(it, SettingsPreferences.getPredictionSelectedModel(it))
            } == true) {
            val reloaded = ModelRuntime.load("predictive_text")
            if (!reloaded) {
                FileLogger.e(TAG, "Reload predictive_text failed after service reclaim")
            }
        }

        try {
            val modelCandidates = if (OnnxAssociationEngine.isInitialized()) {
                try {
                    FragmentCompletion.complete(contextText, OnnxAssociationEngine.predict(contextText, topK * 2)) { text, k ->
                        OnnxAssociationEngine.predict(text, k)
                    }
                }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w(TAG, "Using bundled prediction fallback", error); emptyList() }
            } else emptyList()
            
            val fusedCandidates = fusionEngine.fuseCandidates(modelCandidates, contextText)
            
            fusedCandidates.take(topK)
            
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    fun recordInput(text: String) {
        if (!isInitialized) return
        fusionEngine.recordUserInput(text)
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
        // Explicit off/model switch must also release a HOT model.
        ModelRuntime.releaseWarm("predictive_text")
        OnnxAssociationEngine.release()
    }

    /** 键盘收起较久：卸载联想模型让 :inference 进程可被回收，词频缓存与学习数据保留。 */
    fun releaseModelWhileIdle() {
        if (!isInitialized || !OnnxAssociationEngine.isInitialized()) return
        ModelRuntime.releaseWarm("predictive_text")
        OnnxAssociationEngine.release()
        FileLogger.i(TAG, "Prediction model released while keyboard idle")
    }

    /** 键盘再次弹出：后台重载闲置时卸载的联想模型，首次上屏前通常已就绪。 */
    suspend fun restoreModelAfterIdle() = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext
        if (!isInitialized || OnnxAssociationEngine.isInitialized() ||
            !ModelManager.isModelReady(ctx, SettingsPreferences.getPredictionSelectedModel(ctx))) return@withContext
        val token = generation.get()
        if (ModelRuntime.load("predictive_text") && generation.get() == token) {
            ModelRuntime.keepWarm("predictive_text")
            OnnxAssociationEngine.startWarmup()
        }
    }
}
