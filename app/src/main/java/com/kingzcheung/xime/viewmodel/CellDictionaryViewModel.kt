package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.CellDictionaryManager
import com.kingzcheung.xime.settings.CellDictionaryOffer
import com.kingzcheung.xime.settings.CellDictionaryState
import com.kingzcheung.xime.settings.CellWordAutoUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CellDictionaryUiState(
    val dictionaries: CellDictionaryState = CellDictionaryState(),
    val busy: Boolean = true,
    val message: String? = null,
    val error: Boolean = false,
    val autoUpdate: Boolean = true,
)

class CellDictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val store = CellDictionaryManager.store(context)
    private val state = MutableStateFlow(CellDictionaryUiState())
    val uiState = state.asStateFlow()

    init {
        state.update { it.copy(autoUpdate = CellWordAutoUpdate.isEnabled(context)) }
        work("读取词库…", initially = true) { null }
    }

    fun setAutoUpdate(enabled: Boolean) {
        CellWordAutoUpdate.setEnabled(context, enabled)
        state.update { it.copy(autoUpdate = enabled) }
    }

    /** 开关的保存排队写，下载、导入、应用等等它们写完再读，读到的一定是最后拨的样子。 */
    private val writes = Mutex()

    private fun work(message: String, initially: Boolean = false, action: suspend () -> String?) {
        if (state.value.busy && !initially) return
        state.update { it.copy(busy = true, message = message, error = false) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { writes.withLock { action() to store.read() } }
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
        "已下载，点「应用更改」后生效"
    }

    fun import(uri: Uri) = work("正在导入词库…") {
        val entry = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }
            .use(store::installFile)
        store.select(entry.id, true)
        val skipped = if (entry.skipped > 0) "，跳过 ${entry.skipped} 行（单字或没有拼音）" else ""
        "已导入「${entry.name}」${entry.count + entry.english} 个词$skipped，点「应用更改」后生效"
    }

    /**
     * 拨开关只记下选择：页面立刻按新选择显示，后台写入，不进忙碌状态、不出「保存…」提示。
     * 以前每拨一下都插一行进度条和提示再删掉、英文候选开关下多一行「正在处理词库」，整页跟着上下跳。
     */
    private fun saveChoice(change: (CellDictionaryState) -> CellDictionaryState, write: () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(dictionaries = change(it.dictionaries)) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { writes.withLock { write() } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val actual = runCatching { withContext(Dispatchers.IO) { store.read() } }.getOrNull()
                state.update { it.copy(dictionaries = actual ?: it.dictionaries,
                    message = error.message ?: "保存失败，请重试", error = true) }
            }
        }
    }

    fun select(id: String, selected: Boolean) = saveChoice(
        { it.copy(selected = if (selected) it.selected + id else it.selected - id) }) { store.select(id, selected) }

    fun selectEnglish(selected: Boolean) = saveChoice({ it.copy(selectedEnglish = selected) }) {
        store.selectEnglish(selected)
    }

    fun checkWordUpdates() = work("正在检查词库更新…") {
        if (CellDictionaryManager.checkWordUpdates(context)) "词库更新已下载，点「应用更改」后生效"
        else if (store.read().pending) "没有更新的词表，已有更改尚未应用"
        else "词库已是最新"
    }

    fun restoreBuiltin(id: String) = work("恢复词库…") {
        CellDictionaryManager.restoreBuiltin(context, id)
        store.select(id, true)
        "已恢复，点「应用更改」后生效"
    }

    fun remove(id: String) = work("删除词库…") {
        val current = store.read()
        if (id in current.applied) {
            store.select(id, false)
            RimeConfigHelper.applyCellDictionaries(context)
        }
        val engineCopy = java.io.File(com.kingzcheung.xime.settings.SchemaManager.getRimeDir(context),
            "cyime_cells/${store.dictionaryFile(id).name}")
        check(!engineCopy.exists() || engineCopy.delete()) { "删除输入引擎词库副本失败，请重试" }
        store.remove(id)
        "已删除"
    }

    fun apply() = work("正在应用词库，输入法会短暂暂停…") {
        RimeConfigHelper.applyCellDictionaries(context)
        "词库更改已生效"
    }
}
