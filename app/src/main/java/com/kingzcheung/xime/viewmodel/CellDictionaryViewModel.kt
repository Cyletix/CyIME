package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.CellDictionaryManager
import com.kingzcheung.xime.settings.CellDictionaryOffer
import com.kingzcheung.xime.settings.CellDictionaryState
import com.kingzcheung.xime.settings.ScelDictionary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CellDictionaryUiState(
    val dictionaries: CellDictionaryState = CellDictionaryState(),
    val busy: Boolean = true,
    val message: String? = null,
    val error: Boolean = false,
)

class CellDictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val store = CellDictionaryManager.store(context)
    private val state = MutableStateFlow(CellDictionaryUiState())
    val uiState = state.asStateFlow()

    init { work("读取词库…", initially = true) { null } }

    private fun work(message: String, initially: Boolean = false, action: suspend () -> String?) {
        if (state.value.busy && !initially) return
        state.update { it.copy(busy = true, message = message, error = false) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() to store.read() }
                state.update { it.copy(dictionaries = result.second, message = result.first, error = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update { it.copy(message = error.message ?: "操作失败，请重试", error = true) }
            } finally { state.update { it.copy(busy = false) } }
        }
    }

    fun download(offer: CellDictionaryOffer) = work("正在下载 ${offer.name}…") {
        val installed = store.install(CellDictionaryManager.download(offer), offer)
        store.select(installed.id, true)
        "已下载 ${installed.count} 条，点击「应用词库更改」生效"
    }

    fun import(uri: Uri) = work("正在导入词库…") {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }
            .use(ScelDictionary::readBounded)
        val entry = store.install(bytes)
        store.select(entry.id, true)
        "已导入「${entry.name}」${entry.count} 条，点击「应用词库更改」生效"
    }

    fun select(id: String, selected: Boolean) = work("保存选择…") {
        store.select(id, selected)
        null
    }

    fun apply() = work("正在应用词库，输入法会短暂暂停…") {
        RimeConfigHelper.applyCellDictionaries(context)
        "词库更改已生效"
    }
}
