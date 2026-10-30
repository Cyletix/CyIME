package com.kingzcheung.xime.service

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PredictionManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val onPredictionResult: (List<String>) -> Unit,
) {
    companion object {
        private const val TAG = "PredictionManager"
        private const val MAX_CONTEXT_LENGTH = 25
        const val MAX_ASSOCIATION_COUNT = 20
    }
    
    private var _lastCommittedText = ""
    val lastCommittedText: String get() = _lastCommittedText

    /**
     * 预测请求代际号：退格/清空等改变上下文的操作调用 [invalidatePendingPredictions]，
     * 使所有在途的异步预测结果失效。否则长按退格删除时，旧的联想结果会迟到并反复
     * 回填 associationCandidates，候选栏在"显示联想词 ↔ 空"之间闪动（一闪一闪）。
     */
    private val latest = LatestPrediction(serviceScope, predict = { text ->
        try {
            if (!SettingsPreferences.isSmartPredictionEnabled(context)) emptyList()
            else if (!AssociationManager.isInitialized() && !AssociationManager.initialize(context)) emptyList()
            else AssociationManager.predict(text, MAX_ASSOCIATION_COUNT).map { it.text }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { Log.e(TAG, "Prediction failed", error); emptyList() }
    }, deliver = onPredictionResult)

    val hasPendingPrediction: Boolean get() = latest.isPending
    val requestRevision: Long get() = latest.revision
    val pendingRequest: StateFlow<Long> get() = latest.pendingRequest

    fun invalidatePendingPredictions(): Boolean = latest.invalidate()

    /**
     * 上屏后联想要等一次 Main post 才发请求。这段空档也算“等联想”：期间按退格只取消联想、
     * 不删正文（todo 139，实体键盘按得快时正好落在空档里）。返回值与 [requestRevision] 相同，
     * post 里没有发出请求时用 [releasePrediction] 归还。不会出联想（关了智能联想、单次联想刚用过）
     * 时不登记，退格照常删字。
     */
    fun reservePrediction(): Long =
        if (!suppressNextPrediction && SettingsPreferences.isSmartPredictionEnabled(context)) latest.reserve()
        else latest.revision

    fun releasePrediction(token: Long) = latest.release(token)

    /**
     * 屏幕退格按下：在等的联想立即作废（按住时不让它冒出来），但松手那一下退格仍只算取消联想、
     * 不删正文（todo 139：以前按下就清掉了“在等”，松手时退格当成空闲去删字）。
     */
    fun cancelForDeletePress(): Boolean = latest.invalidateAndHold()

    /**
     * 单次联想抑制标志：联想候选上屏（点击/空格）前置位，使 commitText 触发的下一轮
     * 自动推理被跳过并清空联想候选（候选栏干净），等待用户下一次真实输入。
     * 连续联想模式不置位——commitText 的自动推理即为"一直上屏一直推理"。
     */
    @Volatile
    private var suppressNextPrediction = false

    fun suppressNextPredictionOnce() {
        suppressNextPrediction = true
    }
    
    fun appendCommittedText(text: String) {
        _lastCommittedText = (_lastCommittedText + text).takeLast(MAX_CONTEXT_LENGTH)
    }
    
    fun replaceCommittedText(text: String) {
        _lastCommittedText = text.takeLast(MAX_CONTEXT_LENGTH)
    }

    fun matchesCommittedContext(textBeforeCursor: String): Boolean =
        textBeforeCursor.takeLast(MAX_CONTEXT_LENGTH) == _lastCommittedText

    fun clearCommittedText() {
        _lastCommittedText = ""
    }
    
    fun deleteLastChar() {
        if (_lastCommittedText.isNotEmpty()) {
            _lastCommittedText = _lastCommittedText.dropLastCharacter()
        }
    }
    
    fun initialize() {
        FileLogger.i(TAG, "Initializing association system")
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val trieInit = AssociationService.initialize(context)
                if (trieInit) {
                    FileLogger.i(TAG, "Trie association service initialized")
                } else {
                    FileLogger.w(TAG, "Trie association service initialization failed")
                }
                
                if (!ExtensionManager.isInitialized()) {
                    FileLogger.d(TAG, "ExtensionManager not initialized, initializing...")
                    ExtensionManager.initialize(context)
                }
                
                if (SettingsPreferences.isSmartPredictionEnabled(context)) {
                    try {
                        val initialized = AssociationManager.initialize(context)
                        if (initialized) {
                            FileLogger.i(TAG, "Smart prediction initialized")
                        } else {
                            FileLogger.w(TAG, "Smart prediction initialization failed")
                        }
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Failed to initialize smart prediction: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize association system: ${e.message}")
            }
        }
    }
    
    fun getPrediction(contextText: String) {
        latest.invalidate()
        // 单次联想：消费抑制标志——联想上屏引发的本轮推理不执行，回调空结果清空候选栏
        if (suppressNextPrediction) {
            suppressNextPrediction = false
            onPredictionResult(emptyList())
            return
        }

        if (!canPredictAfter(contextText)) {
            onPredictionResult(emptyList())
            return
        }
        
        if (!SettingsPreferences.isSmartPredictionEnabled(context)) {
            onPredictionResult(emptyList())
            return
        }
        
        latest.submit(contextText)
    }
    
    fun recordInput(text: String) {
        // 无痕只停止学习，联想仍按已有数据给出。
        if (com.kingzcheung.xime.settings.IncognitoState.active) return
        if (SettingsPreferences.isSmartPredictionEnabled(context) && AssociationManager.isInitialized()) {
            AssociationManager.recordInput(text)
        }
    }
    
    fun recordInputPair(lastChar: String, candidate: String) {
        if (com.kingzcheung.xime.settings.IncognitoState.active) return
        if (SettingsPreferences.isSmartPredictionEnabled(context) && AssociationManager.isInitialized()) {
            AssociationManager.recordInput(lastChar + candidate)
        }
    }
    
    suspend fun getEnglishAssociations(text: String, limit: Int = MAX_ASSOCIATION_COUNT): List<String> {
        return try {
            AssociationService.getAssociations(context, text, true, limit)
        } catch (e: Exception) {
            Log.e(TAG, "English association failed", e)
            emptyList()
        }
    }
    
    suspend fun getChineseAssociations(text: String, limit: Int = MAX_ASSOCIATION_COUNT): List<String> {
        return try {
            if (!AssociationManager.isInitialized()) {
                AssociationManager.initialize(context)
            }
            
            val candidates = AssociationManager.predict(text, limit)
            candidates.map { it.text }
        } catch (e: Exception) {
            Log.e(TAG, "Chinese association failed", e)
            emptyList()
        }
    }
}

/**
 * 退格删掉的是一个用户看到的字：代理对（😀、𠮷）、带组合符号的字母、ZWJ 拼成的 emoji 都整个删，
 * 联想上下文不能留下半个代理项（审计 F10：以前 dropLast(1) 把 `a😀` 删成 `a\uD83D`）。
 */
internal fun String.dropLastCharacter(): String {
    if (isEmpty()) return this
    val boundaries = java.text.BreakIterator.getCharacterInstance()
    boundaries.setText(this)
    val start = boundaries.preceding(length)
    return if (start == java.text.BreakIterator.DONE) "" else substring(0, start)
}
