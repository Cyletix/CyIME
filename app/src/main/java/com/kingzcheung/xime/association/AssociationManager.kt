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
    
    private lateinit var fusionEngine: NgramFusionEngine
    private var context: Context? = null
    private var learningCache: UserNgramCache? = null
    private fun learningCache(ctx: Context): UserNgramCache = synchronized(this) {
        learningCache ?: UserNgramCache(ctx.applicationContext).also { learningCache = it }
    }
    
    suspend fun initialize(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        context = ctx
        if (isInitialized) {
            FileLogger.d(TAG, "Already initialized")
            return@withContext true
        }
        
        mutex.withLock {
            if (isInitialized) {
                FileLogger.d(TAG, "Already initialized (in lock)")
                return@withContext true
            }
            
            try {
                fusionEngine = NgramFusionEngine(ctx, learningCache(ctx))
                
                ModelRuntime.register(
                    id = "predictive_text",
                    loader = { OnnxAssociationEngine.initialize(ctx) },
                    releaser = { OnnxAssociationEngine.release() },
                    label = "智能联想模型"
                )
                val modelLoaded = ModelManager.isModelReady(ctx, SettingsPreferences.getPredictionSelectedModel(ctx)) &&
                    ModelRuntime.load("predictive_text")
                FileLogger.i(TAG, "ModelRuntime.load(predictive_text) result: $modelLoaded")

                if (modelLoaded) {
                    ModelRuntime.keepWarm("predictive_text")
                    OnnxAssociationEngine.startWarmup()
                }

                val cacheInit = fusionEngine.initialize()
                FileLogger.i(TAG, "NgramFusionEngine init result: $cacheInit")
                
                isInitialized = cacheInit || modelLoaded
                
                FileLogger.i(TAG, "AssociationManager initialized: model=$modelLoaded, cache=$cacheInit")
                isInitialized
                
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
                try { OnnxAssociationEngine.predict(contextText, topK * 2) }
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
        if (isInitialized) {
            ModelRuntime.unload("predictive_text")
            isInitialized = false
            context = null
        }
    }
}
