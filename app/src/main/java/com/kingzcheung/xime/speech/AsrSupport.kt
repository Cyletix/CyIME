package com.kingzcheung.xime.speech

import android.content.Context
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.util.FileLogger

/**
 * 本地 sherpa-onnx 离线识别后端支持。
 *
 * 模型生命周期：
 * - 打开"使用本地模型"开关时 [warmup] 加载模型并常驻 :asr 服务；
 * - 语音时 [create] 复用后端，切换模型后在工作线程重新加载；
 * - 关闭开关时 [releaseModel] 卸载并解绑服务。
 */
internal object AsrSupport {

    private const val TAG = "AsrSupport"

    @Volatile
    private var warmBackend: OfflineAsrBackend? = null

    /** 初始化前先登记同一个后端，预热与首次录音无论谁先到都不会创建第二个。 */
    fun create(context: Context): AsrBackend? = synchronized(this) {
        warmBackend ?: OfflineAsrBackend(context.applicationContext).also { warmBackend = it }
    }

    fun getLocalName(): String? = "本地离线语音"

    /** 加载本地模型并保持 :asr 服务常驻，直到 [releaseModel]。 */
    fun warmup(context: Context, language: InputLanguage = InputLanguage.CHINESE) {
        synchronized(this) {
            val backend = warmBackend ?: OfflineAsrBackend(context.applicationContext).also { warmBackend = it }
            if (!backend.initialize(language)) {
                FileLogger.e(TAG, "warmup failed")
                return
            }
            FileLogger.i(TAG, "offline ASR model warmed up and resident")
        }
    }

    /** 卸载常驻模型并解绑 :asr 服务。 */
    fun releaseModel() {
        synchronized(this) {
            val backend = warmBackend
            warmBackend = null
            backend?.releaseModel()
            FileLogger.i(TAG, "offline ASR model released")
        }
    }
}
