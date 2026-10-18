package com.kingzcheung.xime.association

import android.content.Context
import com.kingzcheung.xime.model.ModelRuntime
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.service.InferenceClient
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

object OnnxAssociationEngine {
    private const val TAG = "OnnxAssociationEngine"
    private val lifecycle = Mutex()
    private val stateLock = Any()
    private val generation = AtomicLong()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var inferenceClient: InferenceClient? = null
    @Volatile private var loadedModelId: String? = null
    private var warmupStarted = false

    suspend fun initialize(context: Context): Boolean = lifecycle.withLock {
        val app = context.applicationContext
        val modelId = SettingsPreferences.getPredictionSelectedModel(app)
        if (loadedModelId == modelId && inferenceClient?.isBound() == true) return@withLock true
        val token = generation.get()
        ModelRuntime.register("predictive_text", { initialize(app) }, { release() }, "智能联想模型")
        val old = synchronized(stateLock) {
            inferenceClient.also { inferenceClient = null; loadedModelId = null; warmupStarted = false }
        }
        dispose(old)
        ModelRuntime.markUnloaded("predictive_text")
        ModelStorage.migrateLegacyForModel(app, modelId)
        val dir = ModelStorage.getModelDir(app, modelId)
        val model = File(dir, "model_int8_dynamic.onnx")
        val vocab = File(dir, "vocab.json")
        if (!model.isFile || !vocab.isFile) return@withLock false
        val client = InferenceClient(app)
        var installed = false
        try {
            client.onDisconnected = {
                synchronized(stateLock) {
                    if (inferenceClient === client) {
                        loadedModelId = null
                        warmupStarted = false
                        ModelRuntime.markUnloaded("predictive_text")
                    }
                }
            }
            if (!client.ensureBound() || !client.loadModel(InferenceClient.MODEL_PREDICTION, model.path, vocab.path)) {
                return@withLock false
            }
            currentCoroutineContext().ensureActive()
            synchronized(stateLock) {
                if (generation.get() == token && SettingsPreferences.getPredictionSelectedModel(app) == modelId) {
                    inferenceClient = client
                    loadedModelId = modelId
                    installed = true
                    ModelRuntime.markLoaded("predictive_text")
                }
            }
            if (installed) FileLogger.i(TAG, "Prediction model loaded: $modelId")
            installed
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            FileLogger.e(TAG, "Prediction model load failed", error)
            false
        } finally {
            if (!installed) withContext(NonCancellable) { dispose(client) }
        }
    }

    suspend fun predict(inputText: String, topK: Int = 20): List<AssociationCandidate> {
        val client = inferenceClient ?: return emptyList()
        if (loadedModelId == null) return emptyList()
        return client.predict(inputText, topK)
    }

    fun startWarmup() {
        val client = synchronized(stateLock) {
            if (loadedModelId == null || warmupStarted) return
            warmupStarted = true
            inferenceClient
        } ?: return
        scope.launch {
            lifecycle.withLock {
                if (inferenceClient === client) client.predict("，", 5)
            }
        }
    }

    // Invalidate immediately, but never wait for a running inference on the UI thread.
    // UNDISPATCHED queues cleanup at the mutex before a later initialize can load a new model.
    fun release() {
        val client = synchronized(stateLock) {
            generation.incrementAndGet()
            loadedModelId = null
            warmupStarted = false
            ModelRuntime.markUnloaded("predictive_text")
            inferenceClient.also { inferenceClient = null }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lifecycle.withLock { withContext(Dispatchers.IO) { dispose(client) } }
        }
    }

    private suspend fun dispose(client: InferenceClient?) {
        if (client == null) return
        client.onDisconnected = null
        try { client.unloadModel(InferenceClient.MODEL_PREDICTION) }
        finally { client.unbind() }
    }

    fun isInitialized(): Boolean = loadedModelId != null && inferenceClient?.isBound() == true
    fun activeModelId(): String? = loadedModelId.takeIf { isInitialized() }
}
