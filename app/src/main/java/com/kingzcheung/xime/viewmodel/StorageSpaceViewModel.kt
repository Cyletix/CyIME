package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.service.AsrInferenceClient
import com.kingzcheung.xime.settings.MemoryStats
import com.kingzcheung.xime.settings.MemoryStats.withVoice
import com.kingzcheung.xime.settings.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 存储空间页状态：类目统计（后台扫描）、清理进行中标记与结果消息。
 * 统计结果为 null 表示首次扫描尚未完成（UI 显示加载态）。
 */
class StorageSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _categories = MutableStateFlow<List<StorageStats.Category>?>(null)
    val categories: StateFlow<List<StorageStats.Category>?> = _categories.asStateFlow()

    /** 正在清理的类目 id；null 表示空闲 */
    private val _cleaningId = MutableStateFlow<String?>(null)
    val cleaningId: StateFlow<String?> = _cleaningId.asStateFlow()

    /** 清理结果消息（UI 消费后调 [consumeMessage] 置空） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 运行内存；null 表示还没读到。 */
    private val _memory = MutableStateFlow<MemoryStats.Snapshot?>(null)
    val memory: StateFlow<MemoryStats.Snapshot?> = _memory.asStateFlow()

    private val _releasingVoice = MutableStateFlow(false)
    val releasingVoice: StateFlow<Boolean> = _releasingVoice.asStateFlow()

    /** 语音进程在跑时才绑定，页面关掉时解绑；绑着期间语音进程不会因为这一页被拉起或多留模型。 */
    private val voiceClient = AsrInferenceClient(application)
    private var voiceBound = false

    init {
        refresh()
    }

    /** 读各进程，语音那行问语音进程本身（正式包里读不到它的 /proc，11-04 平板实测）。 */
    private suspend fun readMemory(): MemoryStats.Snapshot? {
        val snapshot = withContext(Dispatchers.IO) { runCatching { MemoryStats.read(getApplication()) }.getOrNull() }
            ?: return null
        if (snapshot.voice == null) return snapshot
        voiceBound = true
        return snapshot.withVoice(voiceClient.memoryKb())
    }

    fun refreshMemory() {
        viewModelScope.launch { _memory.value = readMemory() }
    }

    override fun onCleared() {
        if (voiceBound) voiceClient.unbind()
        super.onCleared()
    }

    /** 立刻卸掉语音模型；只在语音进程在跑时给按钮，不会为此拉起进程。 */
    fun releaseVoice() {
        if (_releasingVoice.value) return
        viewModelScope.launch {
            _releasingVoice.value = true
            val before = _memory.value?.voice?.pssBytes
            voiceBound = true
            val released = voiceClient.releaseIdleModelsNow()
            // 模型在 :asr 的后台线程里关掉（等上一句识别收尾），原生内存还回系统也要一点时间：最多等 3 秒，降下来就停。
            var after = readMemory()
            if (released) for (attempt in 0 until 6) {
                val voice = after?.voice?.pssBytes ?: break
                if (before != null && voice < before - RELEASED_DROP) break
                kotlinx.coroutines.delay(500)
                after = readMemory()
            }
            com.kingzcheung.xime.util.FileLogger.i("StorageSpace", "release voice: released=$released " +
                "before=${before?.shr(20)} MB after=${after?.voice?.pssBytes?.shr(20)} MB")
            _memory.value = after
            _releasingVoice.value = false
            val now = after?.voice?.pssBytes ?: 0L
            _message.value = when {
                !released -> "正在用语音或字幕，没有释放"
                before != null && before > now -> "语音模型已释放，空出 ${(before - now) / (1024 * 1024)} MB"
                else -> "语音模型已释放"
            }
        }
    }

    fun refresh() {
        refreshMemory()
        viewModelScope.launch {
            _categories.value = withContext(Dispatchers.IO) {
                StorageStats.collectCategories(getApplication())
            }
        }
    }

    fun clear(categoryId: String, title: String) {
        if (_cleaningId.value != null) return
        viewModelScope.launch {
            _cleaningId.value = categoryId
            val ok = withContext(Dispatchers.IO) {
                try {
                    StorageStats.clearCategory(getApplication(), categoryId)
                } catch (e: Exception) {
                    null
                }
            }
            _cleaningId.value = null
            _message.value = when (ok) {
                true -> "$title 已清理"
                false -> "$title 清理失败，请重试"
                null -> "$title 清理出错"
            }
            refresh()
        }
    }

    private companion object {
        /** 降了这么多才算模型真卸掉了（只是抖动不算）。 */
        const val RELEASED_DROP = 20L * 1024 * 1024
    }

    fun consumeMessage() {
        _message.value = null
    }
}
