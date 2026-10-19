package com.kingzcheung.xime.ui.settings

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.handwriting.training.BuiltinTrainingPrompts
import com.kingzcheung.xime.handwriting.training.FormulaGenerator
import com.kingzcheung.xime.handwriting.training.PromptBankFile
import com.kingzcheung.xime.handwriting.training.TrainingBank
import com.kingzcheung.xime.handwriting.training.FormulaCsvParser
import com.kingzcheung.xime.handwriting.HandwritingEngine
import com.kingzcheung.xime.handwriting.HandwritingTemplates
import com.kingzcheung.xime.handwriting.PenEraser
import com.kingzcheung.xime.handwriting.training.TrainingInk
import com.kingzcheung.xime.handwriting.training.TrainingMiss
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.handwriting.training.TrainingPoint
import com.kingzcheung.xime.handwriting.training.TrainingPrompt
import com.kingzcheung.xime.handwriting.training.TrainingPromptType
import com.kingzcheung.xime.handwriting.training.TrainingSample
import com.kingzcheung.xime.handwriting.training.TrainingSampleStore
import com.kingzcheung.xime.handwriting.training.TrainingStroke
import com.kingzcheung.xime.ui.common.CompactDropdownMenu
import com.kingzcheung.xime.ui.common.CompactMenuRow
import com.kingzcheung.xime.service.FeedbackManager
import com.kingzcheung.xime.ui.keyboard.StylusPresence
import com.kingzcheung.xime.ui.keyboard.isErasing
import com.kingzcheung.xime.ui.keyboard.isPen
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TRAINING_TAG = "HandwritingTraining"

private fun TrainingBank.isExternal() =
    this == TrainingBank.MathFormula || this == TrainingBank.Japanese || this == TrainingBank.English ||
        this == TrainingBank.Chinese

/** 三星 S Pen 插拔广播（粘性），extra 为 true 表示笔已插回。 */
private const val SPEN_INSERT_ACTION = "com.samsung.pen.INSERT"
private const val SPEN_INSERT_EXTRA = "penInsert"

data class TrainingUiState(
    val bank: TrainingBank = TrainingBank.entries.first(),
    val prompts: List<TrainingPrompt> = emptyList(),
    val index: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
    val loading: Boolean = true,
    /** 从剪贴板粘贴的临时题，写完或跳过后回到题库原位置。 */
    val custom: TrainingPrompt? = null,
    /** 当前题的识别检查：认错后要求再写几遍。 */
    val check: TrainingCheck? = null,
    /** 正在识别刚写的字，期间不能再按“下一题”。 */
    val checking: Boolean = false,
    /** 个人手写模型：null 没有，true 在用，false 已停用。 */
    val personalModel: Boolean? = null,
) {
    val current: TrainingPrompt? get() = custom ?: prompts.getOrNull(index)
    val totalSamples: Int get() = counts.values.sum()
}

/**
 * 识别检查状态：这题认错后要一直重写到认对为止。[wrong] 是第一次认错时模型给的答案，
 * [retries] 是认错后已经重写了几遍，[last] 是刚写那一遍的识别结果。
 */
data class TrainingCheck(val promptId: String, val wrong: String, val retries: Int, val last: String)

sealed class TrainingEvent {
    data class Message(val text: String) : TrainingEvent()
    data class Share(val uri: Uri) : TrainingEvent()
}

/** 训练模式：只做数据收集，按题库顺序出题，样本逐条写入 app 私有目录。 */
class HandwritingTrainingViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val store = TrainingSampleStore(app)
    private var formulaBank: List<TrainingPrompt> = emptyList()
    private val assetBanks = java.util.concurrent.ConcurrentHashMap<PromptBankFile, List<TrainingPrompt>>()

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state

    private val _events = MutableSharedFlow<TrainingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TrainingEvent> = _events

    private var misses: List<TrainingMiss> = emptyList()

    /** 内置手写模型能认的字；只有单个字且在这里面的题才做识别检查（假名、长句模型不认识，检查没有意义）。 */
    private val vocab: Set<String> by lazy {
        runCatching {
            val file = java.io.File(ModelStorage.getModelDir(app, "ochwpro"), "char_index.json")
            val chars = org.json.JSONObject(file.readText(Charsets.UTF_8).removePrefix("﻿")).getJSONArray("chars")
            (0 until chars.length()).map { chars.getString(it) }.filter { it.isNotEmpty() }.toSet()
        }.getOrElse { e -> Log.w(TRAINING_TAG, "load handwriting vocab failed", e); emptySet() }
    }

    init {
        viewModelScope.launch {
            val (bank, counts) = withContext(Dispatchers.IO) {
                // 早先字母、符号题也会被判错记进来，现在只留汉字
                misses = store.retainMisses { enforcesCheck(it.prompt) }
                store.loadFormulaBank() to store.countsByPrompt()
            }
            formulaBank = bank
            _state.update { it.copy(counts = counts, personalModel = personalModelState()) }
            selectBank(store.currentBank)
        }
    }

    private fun assetFor(bank: TrainingBank): PromptBankFile? = when (bank) {
        TrainingBank.MathFormula -> PromptBankFile.Math
        TrainingBank.Japanese -> PromptBankFile.Japanese
        TrainingBank.English -> PromptBankFile.English
        TrainingBank.Chinese -> PromptBankFile.Chinese
        else -> null
    }

    private fun promptsFor(bank: TrainingBank): List<TrainingPrompt> = when (bank) {
        TrainingBank.Kana -> BuiltinTrainingPrompts.prompts(TrainingPromptType.Kana)
        TrainingBank.Latin -> BuiltinTrainingPrompts.prompts(TrainingPromptType.Latin)
        TrainingBank.Symbol -> BuiltinTrainingPrompts.prompts(TrainingPromptType.Symbol)
        TrainingBank.MathFormula -> mixImported(assetBanks[PromptBankFile.Math].orEmpty(), formulaBank)
        // 日语只出单词和语法点：整句太长，而且没有日语手写模型，单字认不了的话整句也用不上
        TrainingBank.Japanese -> assetBanks[PromptBankFile.Japanese].orEmpty().filter { !it.long && it.kind != "sentence" }
        TrainingBank.English, TrainingBank.Chinese -> assetBanks[assetFor(bank)].orEmpty()
        TrainingBank.Missed -> misses.map { it.prompt.copy(hint = "认错 ${it.misses} 次，上次认成「${it.lastWrong}」") }
        TrainingBank.RandomFormula -> randomFormulas
        TrainingBank.ImportedFormula -> formulaBank
    }

    private val randomFormulas by lazy { FormulaGenerator.prompts() }

    /** 自己导入的公式穿插进真实公式里：每 4 题出 1 道导入题，导入题出完后全是题库里的。 */
    private fun mixImported(math: List<TrainingPrompt>, imported: List<TrainingPrompt>): List<TrainingPrompt> {
        if (imported.isEmpty()) return math
        if (math.isEmpty()) return imported
        val out = ArrayList<TrainingPrompt>(math.size + imported.size)
        var m = 0
        var i = 0
        while (m < math.size || i < imported.size) {
            if (i < imported.size && (out.size % 4 == 0 || m >= math.size)) out += imported[i++] else out += math[m++]
        }
        return out
    }

    /** 切换题库；外置大题库第一次用到时才从文件读入（英语约 4 MB），读完前显示加载中。没放题库文件时为空。 */
    fun selectBank(bank: TrainingBank) {
        store.currentBank = bank
        val asset = assetFor(bank)
        if (asset != null && assetBanks[asset] == null) {
            _state.update { it.copy(bank = bank, prompts = emptyList(), index = 0, custom = null, loading = true) }
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    val file = java.io.File(PromptBankFile.dir(getApplication()), asset.fileName)
                    if (!file.isFile) emptyList() else runCatching {
                        file.bufferedReader(Charsets.UTF_8).useLines { asset.parse(it) }
                    }.getOrElse { e -> Log.w(TRAINING_TAG, "load ${file.name} failed", e); emptyList() }
                }
                assetBanks[asset] = loaded
                if (_state.value.bank == bank) selectBank(bank)
            }
            return
        }
        val prompts = promptsFor(bank)
        val index = store.position(bank).coerceIn(0, (prompts.size - 1).coerceAtLeast(0))
        _state.update { it.copy(bank = bank, prompts = prompts, index = index, custom = null, loading = false, check = null) }
    }

    /** 能做识别检查时返回内置模型的前 5 个候选；不是单个字、模型不认识这个字或模型不可用时返回 null。 */
    /** 只有汉字（内置模型真正认得的）才“认错就重写到认对”。 */
    private fun enforcesCheck(prompt: TrainingPrompt): Boolean =
        isHanzi(prompt.label) && (prompt.type == TrainingPromptType.Chinese || prompt.type == TrainingPromptType.Custom)

    private fun isHanzi(label: String): Boolean =
        label.codePointCount(0, label.length) == 1 &&
            Character.UnicodeScript.of(label.codePointAt(0)) == Character.UnicodeScript.HAN

    private fun recognize(prompt: TrainingPrompt, strokes: List<TrainingStroke>): List<String>? {
        val label = prompt.label
        if (label.codePointCount(0, label.length) != 1 || label !in vocab) return null
        val app = getApplication<android.app.Application>()
        if (!HandwritingEngine.isInitialized() && !HandwritingEngine.initialize(app)) return null
        val top = HandwritingEngine.predict(strokes.map { s -> s.points.map { it.x to it.y } }, topK = 5)
        return top.map { it.char }.ifEmpty { null }
    }

    /** 剪贴板文字作为当前题。 */
    fun pasteCustom(text: String?) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) {
            _events.tryEmit(TrainingEvent.Message("剪贴板里没有文字"))
            return
        }
        _state.update { it.copy(custom = BuiltinTrainingPrompts.custom(value)) }
    }

    /** 保存当前题样本并前进；strokes 为空时不保存。 */
    fun submit(strokes: List<TrainingStroke>, widthPx: Float, heightPx: Float, density: Float) {
        val prompt = _state.value.current ?: return
        if (strokes.isEmpty() || _state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            try {
                val recognized = withContext(Dispatchers.IO) {
                    runCatching { recognize(prompt, strokes) }
                        .onFailure { Log.w(TRAINING_TAG, "recognize failed", it) }.getOrNull()
                }
                val sample = TrainingSample(
                    prompt = prompt,
                    canvasWidthPx = widthPx,
                    canvasHeightPx = heightPx,
                    density = density,
                    strokes = strokes,
                    createdAtMs = System.currentTimeMillis(),
                    recognized = recognized,
                )
                withContext(Dispatchers.IO) {
                    store.append(sample, BuildConfig.VERSION_NAME, AppInfo.deviceModel)
                    // 假名、英文字母同时加成输入法手写的模板（边用边学，见 HandwritingTemplates）
                    runCatching {
                        HandwritingTemplates.addTrainingSample(getApplication(), prompt.type.key, prompt.label, sample.id,
                            strokes.map { s -> s.points.map { it.x to it.y } })
                    }
                }
                _state.update { s -> s.copy(counts = s.counts + (prompt.id to ((s.counts[prompt.id] ?: 0) + 1))) }
                // 字母、符号、假名：内置模型字表里虽有一部分，但它是按汉字训练的，认不准，
                // 只把识别结果记进样本，不判对错、不卡题
                if (recognized == null || !enforcesCheck(prompt)) {
                    advance()
                    return@launch
                }
                val top = recognized.first()
                val matched = top == prompt.label
                misses = withContext(Dispatchers.IO) { store.recordCheck(prompt, matched, top) }
                val pending = _state.value.check?.takeIf { it.promptId == prompt.id }
                if (!matched) {
                    // 认错：留在这题一直写，直到认对一次
                    _state.update {
                        it.copy(check = pending?.copy(retries = pending.retries + 1, last = top)
                            ?: TrainingCheck(prompt.id, top, 0, top))
                    }
                    refreshMissedBank(prompt)
                } else {
                    // 认对一次就说明能认出来，直接下一题
                    _state.update { it.copy(check = null) }
                    if (refreshMissedBank(prompt)) advance()
                }
            } catch (e: Exception) {
                Log.e(TRAINING_TAG, "save sample failed", e)
                FileLogger.e(TRAINING_TAG, "save sample failed", e)
                _events.emit(TrainingEvent.Message("保存失败：${e.message}"))
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    /**
     * 在“认错的字”题库里时，按最新认错列表刷新题目。
     * 返回是否还需要 [advance]：刚写的题被移出列表时，后面的题已经顶到当前位置，不用再前进。
     */
    private fun refreshMissedBank(prompt: TrainingPrompt): Boolean {
        val s = _state.value
        if (s.bank != TrainingBank.Missed || s.custom != null) return true
        val prompts = promptsFor(TrainingBank.Missed)
        val kept = prompts.indexOfFirst { it.id == prompt.id }
        val index = (if (kept >= 0) kept else s.index).coerceIn(0, (prompts.size - 1).coerceAtLeast(0))
        _state.update { it.copy(prompts = prompts, index = index) }
        store.setPosition(TrainingBank.Missed, index)
        return kept >= 0
    }

    fun skip() = advance()

    private fun personalModelState(): Boolean? {
        val app = getApplication<android.app.Application>()
        return when {
            HandwritingEngine.personalModelFile(app).isFile -> true
            HandwritingEngine.disabledPersonalModelFile(app).isFile -> false
            else -> null
        }
    }

    /** 在个人模型和内置模型之间切换；训练页的识别检查和输入法手写板都跟着换。 */
    fun togglePersonalModel() {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            val target = _state.value.personalModel != true
            val active = withContext(Dispatchers.IO) { HandwritingEngine.setPersonalModelEnabled(app, target) }
            _state.update { it.copy(personalModel = personalModelState()) }
            _events.emit(TrainingEvent.Message(if (active) "已换成个人手写模型" else "已换回内置手写模型"))
        }
    }

    /** 回到上一题（不删除已保存的样本）；粘贴的临时题直接回到题库原位置。 */
    fun back() {
        _state.update { it.copy(check = null) }
        if (_state.value.custom != null) {
            _state.update { it.copy(custom = null) }
            return
        }
        val s = _state.value
        if (s.index <= 0) return
        store.setPosition(s.bank, s.index - 1)
        _state.update { it.copy(index = s.index - 1) }
    }

    private fun advance() {
        _state.update { it.copy(check = null) }
        if (_state.value.custom != null) {
            _state.update { it.copy(custom = null) }
            return
        }
        val s = _state.value
        if (s.prompts.isEmpty()) return
        val next = s.index + 1
        val wrapped = next >= s.prompts.size
        val index = if (wrapped) 0 else next
        store.setPosition(s.bank, index)
        _state.update { it.copy(index = index) }
        if (wrapped) _events.tryEmit(TrainingEvent.Message("这一组写完一轮了，从头再来"))
    }

    fun importFormulaCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<android.app.Application>()
                val (result, merged) = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取文件")
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "import.csv"
                    val parsed = FormulaCsvParser.parse(bytes, name)
                    parsed to (if (parsed.prompts.isEmpty()) null else store.mergeFormulaBank(name, parsed.prompts))
                }
                merged?.let { formulaBank = it }
                if (result.prompts.isEmpty()) {
                    _events.emit(TrainingEvent.Message("没有读到公式，请确认是 file_name,LaTeX 两列的 CSV"))
                    return@launch
                }
                selectBank(TrainingBank.MathFormula)
                val extra = buildList {
                    if (result.repairedRows > 0) add("${result.repairedRows} 条有坏字节已修复")
                    if (result.skippedRows > 0) add("${result.skippedRows} 行跳过")
                }.joinToString("，")
                _events.emit(
                    TrainingEvent.Message(
                        "已导入 ${result.prompts.size} 道公式题" + (if (extra.isEmpty()) "" else "（$extra）") +
                            "，导入题库共 ${formulaBank.size} 道"
                    )
                )
            } catch (e: Exception) {
                Log.e(TRAINING_TAG, "import csv failed", e)
                FileLogger.e(TRAINING_TAG, "import csv failed", e)
                _events.emit(TrainingEvent.Message("导入失败：${e.message}"))
            }
        }
    }

    /** 导入题库文件（math_formulas.tsv / japanese.tsv / english.tsv / chinese_chars.tsv），可多选；按文件名放进外置题库目录。 */
    fun importBankFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            val imported = mutableListOf<PromptBankFile>()
            val ignored = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val dir = PromptBankFile.dir(app).apply { mkdirs() }
                for (uri in uris) {
                    val name = displayName(app, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
                    val bank = PromptBankFile.byFileName(name)
                    if (bank == null) { ignored += name; continue }
                    val tmp = java.io.File(dir, bank.fileName + ".tmp")
                    app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    val target = java.io.File(dir, bank.fileName)
                    if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
                    assetBanks.remove(bank)
                    imported += bank
                }
            }
            val text = buildString {
                append(if (imported.isEmpty()) "没有可用的题库文件" else "已导入 ${imported.joinToString("、") { it.fileName }}")
                if (ignored.isNotEmpty()) append("；不认识的文件：${ignored.joinToString("、")}")
            }
            _events.emit(TrainingEvent.Message(text))
            if (imported.isNotEmpty()) selectBank(_state.value.bank)
        }
    }

    /**
     * 导入样本备份（之前“导出到下载目录 / 分享样本文件”得到的 .jsonl，可多选）：合并进本机样本，
     * 再把假名、字母补进英文 / 日语手写的模板库。清除数据后这样恢复，不需要 adb。
     */
    fun importSampleBackup(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val added = HashMap<String, Int>()
                    for (uri in uris) app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                        store.importSamples(lines).forEach { (type, n) -> added[type] = (added[type] ?: 0) + n }
                    }
                    for (script in com.kingzcheung.xime.handwriting.HandwritingTemplates.Script.entries)
                        runCatching { com.kingzcheung.xime.handwriting.HandwritingTemplates.importTrainingSamples(app, script) }
                    added
                }
            }
            val added = result.getOrElse { e ->
                Log.e(TRAINING_TAG, "import samples failed", e)
                _events.emit(TrainingEvent.Message("导入失败：${e.message}"))
                return@launch
            }
            val total = added.values.sum()
            val text = buildString {
                if (total == 0) append("没有新样本（文件不是样本备份，或这些样本已经在了）")
                else {
                    append("已导入 $total 条样本")
                    val parts = listOfNotNull(added["kana"]?.let { "假名 $it" }, added["latin"]?.let { "字母 $it" })
                    if (parts.isNotEmpty()) append("（${parts.joinToString("、")}）")
                }
                if (total > 0 && !com.kingzcheung.xime.handwriting.FormulaRecognizer.isAvailable(app))
                    append("；英文、日语手写还要在 模型管理 下载「公式手写模型」")
            }
            _events.emit(TrainingEvent.Message(text))
            if (total > 0) {
                val counts = withContext(Dispatchers.IO) { store.countsByPrompt() }
                _state.update { it.copy(counts = counts) }
            }
        }
    }

    private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    fun export(share: Boolean) {
        viewModelScope.launch {
            try {
                val app = getApplication<android.app.Application>()
                val file = withContext(Dispatchers.IO) { store.snapshotForExport() }
                if (file == null) {
                    _events.emit(TrainingEvent.Message("还没有样本"))
                    return@launch
                }
                if (share) {
                    val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                    _events.emit(TrainingEvent.Share(uri))
                } else {
                    val path = withContext(Dispatchers.IO) { store.saveToDownloads(file) }
                    _events.emit(TrainingEvent.Message("已导出 ${_state.value.totalSamples} 条到 $path"))
                }
            } catch (e: Exception) {
                Log.e(TRAINING_TAG, "export failed", e)
                FileLogger.e(TRAINING_TAG, "export failed", e)
                _events.emit(TrainingEvent.Message("导出失败：${e.message}"))
            }
        }
    }

    fun clearSamples() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearSamples() }
            misses = emptyList()
            _state.update { it.copy(counts = emptyMap(), check = null) }
            if (_state.value.bank == TrainingBank.Missed) selectBank(TrainingBank.Missed)
            _events.emit(TrainingEvent.Message("已清空样本"))
        }
    }
}

/**
 * 手写训练模式（数据收集）：顶部出题，下方全屏画板，按“下一题”保存当前笔迹。
 */
@Composable
fun HandwritingTrainingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HandwritingTrainingViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var bankMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val strokes = remember { mutableStateListOf<TrainingStroke>() }
    var currentStroke by remember { mutableStateOf<List<TrainingPoint>>(emptyList()) }
    // 每次改动前的笔画快照：撤销回到上一步（写了一笔就去掉它，擦了就恢复被擦的笔）
    val history = remember { mutableStateListOf<List<TrainingStroke>>() }
    var sampleStartUptime by remember { mutableStateOf(-1L) }
    var canvasSize by remember { mutableStateOf(0 to 0) }
    val density = LocalDensity.current.density
    val eraserRadiusPx = with(LocalDensity.current) { PenEraser.Radius.toPx() }
    val stylusPresence = remember { StylusPresence() }
    // 用笔时手指不出墨迹（仍可点按钮）：三星笔仓插拔广播说笔已拔出，或本次进入后笔碰过画板；笔插回后恢复手指书写
    val penSeen = remember { mutableStateOf(false) }
    val penDetached = remember { mutableStateOf(false) }
    // 橡皮范围圈：按住侧键或用笔尾时，悬停和擦除都在笔尖画出擦除范围（单独一层，移动时不重画笔迹）。
    // 注意不要按橡皮状态增删画板的 Modifier（例如切换悬停指针）：修饰链一变，下面的手势会被重建，
    // 拖动擦除会在第一下之后中断——1.12.26 “只能点一下擦一次”就是这个原因。
    val eraserAt = remember { mutableStateOf<Offset?>(null) }
    val fingerBlocked = penSeen.value || penDetached.value
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: Intent) {
                if (!intent.hasExtra(SPEN_INSERT_EXTRA)) return
                val inserted = intent.getBooleanExtra(SPEN_INSERT_EXTRA, true)
                penDetached.value = !inserted
                if (inserted) penSeen.value = false
            }
        }
        // 粘性广播：注册时立即拿到当前插拔状态；非三星设备没有这个广播，只靠“笔碰过画板”判断
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(SPEN_INSERT_ACTION), ContextCompat.RECEIVER_EXPORTED,
        )?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val view = LocalView.current
    // 擦除音效与轻震沿用输入法手写板（跟随按键音和振动设置）
    val feedback = remember { FeedbackManager(context.applicationContext).also { it.initialize() } }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    fun resetBoard() {
        strokes.clear()
        history.clear()
        currentStroke = emptyList()
        sampleStartUptime = -1L
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFormulaCsv(uri)
    }
    val bankLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importBankFiles(uris)
    }
    val sampleBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importSampleBackup(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrainingEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is TrainingEvent.Share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newRawUri(null, event.uri)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享训练样本"))
                }
            }
        }
    }
    // 换题时清空画板
    LaunchedEffect(state.bank, state.index, state.custom) { resetBoard() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            // 顶栏：退出、题库切换、进度、更多
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出")
                }
                Box {
                    TextButton(onClick = { bankMenu = true }) {
                        Text(state.bank.title, fontSize = 16.sp)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "切换题库")
                    }
                    CompactDropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                        for (bank in TrainingBank.entries.filter { it.inMenu }) {
                            CompactMenuRow(
                                label = bank.title,
                                selected = bank == state.bank,
                                onClick = { bankMenu = false; viewModel.selectBank(bank) },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.prompts.isEmpty()) "已存 ${state.totalSamples}"
                    else "${state.index + 1}/${state.prompts.size} · 已存 ${state.totalSamples}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    CompactDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        CompactMenuRow(
                            label = "导入公式题库（CSV）", icon = Icons.Filled.FileOpen,
                            onClick = { moreMenu = false; importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                        )
                        CompactMenuRow(
                            label = "导入题库文件（.tsv）", icon = Icons.Filled.FileOpen,
                            onClick = { moreMenu = false; bankLauncher.launch(arrayOf("*/*")) },
                        )
                        CompactMenuRow(
                            label = "导入样本备份（.jsonl）", icon = Icons.Filled.FileOpen,
                            onClick = { moreMenu = false; sampleBackupLauncher.launch(arrayOf("*/*")) },
                        )
                        CompactMenuRow(
                            label = "导出到下载目录", icon = Icons.Filled.SaveAlt,
                            onClick = { moreMenu = false; viewModel.export(share = false) },
                        )
                        CompactMenuRow(
                            label = "分享样本文件", icon = Icons.Filled.IosShare,
                            onClick = { moreMenu = false; viewModel.export(share = true) },
                        )
                        state.personalModel?.let { active ->
                            CompactMenuRow(
                                label = if (active) "换回内置手写模型" else "换成个人手写模型", icon = Icons.Filled.SwapHoriz,
                                onClick = { moreMenu = false; viewModel.togglePersonalModel() },
                            )
                        }
                        CompactMenuRow(
                            label = "清空全部样本", icon = Icons.Filled.Delete,
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { moreMenu = false; confirmClear = true },
                        )
                    }
                }
            }

            // 题目区：长题（推导、长句）加高；提示在题目下方单独一行，不压到画板
            val prompt = state.current
            val long = prompt?.long == true
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (long) 170.dp else 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> Text("正在载入题库…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    prompt == null && state.bank == TrainingBank.ImportedFormula -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有导入公式题库", fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                        }) { Text("导入 CSV（file_name,LaTeX）") }
                    }
                    prompt == null && state.bank.isExternal() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("这个分类的题库文件还没放到平板上", fontSize = 16.sp)
                        Text(
                            "电脑上运行 tools/handwriting_training/build_banks.py --push 会自动放好，也可以手动导入",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { bankLauncher.launch(arrayOf("*/*")) }) { Text("导入题库文件（.tsv）") }
                    }
                    prompt == null && state.bank == TrainingBank.Missed -> Text(
                        "还没有认错的字。写汉字时会自动识别，认错又跳过的字会放到这里",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    prompt == null -> Text("这个分类没有题目", fontSize = 16.sp)
                    prompt.latex != null && prompt.type != TrainingPromptType.Symbol -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KatexView(
                            latex = prompt.latex ?: prompt.label,
                            modifier = Modifier.fillMaxWidth().height(if (long) 150.dp else 92.dp),
                        )
                        if (!long) {
                            Text(
                                text = prompt.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        val length = prompt.label.length
                        Text(
                            prompt.label,
                            fontSize = when {
                                length <= 3 -> 64.sp
                                length <= 12 -> 36.sp
                                length <= 24 -> 28.sp
                                else -> 22.sp
                            },
                            lineHeight = if (length <= 3) 72.sp else 32.sp,
                            maxLines = if (long) 4 else 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (prompt.type == TrainingPromptType.Symbol && prompt.latex != null && prompt.latex != prompt.label) {
                            Spacer(Modifier.width(16.dp))
                            Text(prompt.latex, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val written = prompt?.let { state.counts[it.id] } ?: 0
                val tag = when {
                    state.custom != null -> "粘贴的题"
                    prompt?.kind == "derivation" -> "推导 · 整段写完"
                    long -> "长句 · 整句写完"
                    else -> null
                }
                if (tag != null) {
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                if (written > 0) {
                    Text(
                        text = "这题已写 $written 次",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            state.check?.takeIf { it.promptId == prompt?.id }?.let { check ->
                val text = if (check.retries == 0) "认成了「${check.wrong}」，再写一遍，写到认对为止"
                else "又认成了「${check.last}」，再写一遍（已重写 ${check.retries} 遍）"
                Text(
                    text = text,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).testTag("training-check"),
                )
            }
            prompt?.hint?.let { hint ->
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }

            // 画板
            val inkColor = MaterialTheme.colorScheme.onSurface
            val guideColor = MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 橡皮圈单独一层：只有这层随笔移动重画
            Canvas(Modifier.matchParentSize().zIndex(1f)) {
                eraserAt.value?.let { c ->
                    drawCircle(inkColor.copy(alpha = 0.10f), radius = eraserRadiusPx, center = c)
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = eraserRadiusPx, center = c, style = Stroke(width = 3.dp.toPx()))
                    drawCircle(inkColor, radius = eraserRadiusPx, center = c, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .testTag("training-board")
                    .onSizeChanged { canvasSize = it.width to it.height }
                    .pointerInput(stylusPresence) {
                        // 只观察不消费：悬停事件也要记下笔的存在（用于防手掌误触）
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pen = event.changes.firstOrNull { it.type.isPen() }
                                if (pen != null) {
                                    stylusPresence.mark(SystemClock.uptimeMillis())
                                    penSeen.value = true
                                }
                                val erasingPen = pen?.takeIf {
                                    event.type != PointerEventType.Exit &&
                                        isErasing(it.type, event.motionEvent?.buttonState ?: 0)
                                }
                                // 抬笔时先收起，笔还在悬停的话下一帧悬停事件会马上再显示
                                eraserAt.value = erasingPen?.takeIf { event.type != PointerEventType.Release }?.position
                            }
                        }
                    }
                    .pointerInput(stylusPresence) {
                        // 与输入法手写板 Modifier.handwritingInk 相同的规则：笔尾橡皮或按住侧键是擦除，
                        // 笔落下时接管已按下的手掌，笔悬停或刚离开时手指不画线。
                        awaitEachGesture {
                            val first = awaitFirstDown()
                            var active: PointerId? = null
                            var erasing = false
                            var erasedInGesture = false
                            var lastErase: Offset? = null
                            var tool = "touch"
                            val pts = mutableListOf<TrainingPoint>()

                            fun inside(o: Offset) = o.x >= 0 && o.y >= 0 && o.x < size.width && o.y < size.height

                            fun eraseAlong(points: List<Offset>) {
                                if (points.isEmpty()) return
                                // 橡皮轨迹接上上一帧的最后一点，按线段判断，快速划过也不漏
                                val path = listOfNotNull(lastErase) + points
                                lastErase = points.last()
                                val kept = TrainingInk.erase(strokes.toList(), path.map { it.x to it.y }, eraserRadiusPx)
                                if (kept.size == strokes.size) return
                                if (!erasedInGesture) { history.add(strokes.toList()); erasedInGesture = true }
                                strokes.clear(); strokes.addAll(kept)
                                if (strokes.isEmpty()) sampleStartUptime = -1L
                                feedback.performKeyPressEffect("handwriting_erase", view)
                            }

                            fun addPoints(c: PointerInputChange) {
                                val t0 = sampleStartUptime
                                c.historical.filter { inside(it.position) }
                                    .forEach { pts += TrainingPoint(it.position.x, it.position.y, it.uptimeMillis - t0) }
                                if (inside(c.position)) pts += TrainingPoint(c.position.x, c.position.y, c.uptimeMillis - t0)
                                currentStroke = pts.toList()
                            }

                            fun start(change: PointerInputChange, event: PointerEvent) {
                                if (stylusPresence.rejectsTouch(change.type, change.uptimeMillis)) return
                                if (change.type == PointerType.Touch && (penSeen.value || penDetached.value)) return
                                active = change.id
                                erasing = isErasing(change.type, event.motionEvent?.buttonState ?: 0)
                                lastErase = null
                                pts.clear()
                                if (erasing) {
                                    eraseAlong(listOf(change.position))
                                } else {
                                    if (sampleStartUptime < 0) sampleStartUptime = change.uptimeMillis
                                    tool = when (change.type) {
                                        PointerType.Stylus -> "stylus"
                                        PointerType.Mouse -> "mouse"
                                        else -> "touch"
                                    }
                                    pts += TrainingPoint(change.position.x, change.position.y, change.uptimeMillis - sampleStartUptime)
                                    currentStroke = pts.toList()
                                }
                            }

                            fun stopWriting(cancelled: Boolean) {
                                if (active != null && !erasing && !cancelled && pts.isNotEmpty()) {
                                    history.add(strokes.toList())
                                    strokes.add(TrainingStroke(pts.toList(), tool))
                                }
                                pts.clear()
                                currentStroke = emptyList()
                                active = null
                            }

                            start(first, currentEvent)
                            first.consume()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // 手掌先按住时，笔落下立刻接管；手掌那一笔作废
                                    val pen = event.changes.firstOrNull { it.changedToDown() && it.type.isPen() && it.id != active }
                                    val current = event.changes.firstOrNull { it.id == active }
                                    if (pen != null && (current == null || !current.type.isPen())) {
                                        stopWriting(cancelled = true)
                                        start(pen, event)
                                    } else if (active == null) {
                                        event.changes.firstOrNull { it.changedToDown() }?.let { start(it, event) }
                                    }
                                    event.changes.firstOrNull { it.id == active }?.let { change ->
                                        // 写到一半才按下侧键：这一笔作废，从这里开始擦（侧键状态逐帧判断，不只看落笔那一刻）
                                        if (!erasing && change.pressed && isErasing(change.type, event.motionEvent?.buttonState ?: 0)) {
                                            erasing = true
                                            pts.clear()
                                            currentStroke = emptyList()
                                            lastErase = change.previousPosition  // 从上一帧的位置连过来，不漏中间那段
                                        }
                                        if (erasing) {
                                            eraseAlong((change.historical.map { it.position } + change.position).filter(::inside))
                                        } else {
                                            addPoints(change)
                                        }
                                        if (!change.pressed) stopWriting(cancelled = false)
                                    }
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                            } finally {
                                stopWriting(cancelled = true)
                            }
                        }
                    },
            ) {
                // 一条浅色基线辅助对齐，不影响数据
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                drawLine(guideColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), pathEffect = dash)
                val width = 3.5.dp.toPx()
                fun render(points: List<TrainingPoint>, color: Color) {
                    if (points.isEmpty()) return
                    if (points.size == 1) {
                        drawCircle(color, radius = width / 2, center = Offset(points[0].x, points[0].y)); return
                    }
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                for (s in strokes) render(s.points, inkColor)
                render(currentStroke, inkColor)
            }
            } // 画板 Box

            Text(
                text = if (fingerBlocked) "正在用笔：手指不会在画板上写字，插回笔或重新进入可恢复" else " ",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 右手执笔写字，操作按钮全部靠左给左手按；最常用的“下一题”放最左。
            // 按钮不接收键盘焦点：窗口失焦再回来时焦点会落到按钮上，外接键盘、笔或系统发来的按键
            // （回车、空格、方向确认键，长按还会连发）就会一直点“跳过”，连跳好几题。只允许手点。
            val noKeyFocus = Modifier.focusProperties { canFocus = false }
            Row(
                // 手机竖屏放不下全部按钮时可以横向滑动，不裁掉后面的按钮
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        viewModel.submit(
                            TrainingInk.rebase(strokes.toList()),
                            canvasSize.first.toFloat(),
                            canvasSize.second.toFloat(),
                            density,
                        )
                        resetBoard()
                    },
                    enabled = prompt != null && strokes.isNotEmpty() && !state.checking,
                    modifier = noKeyFocus,
                ) {
                    Text(if (state.check?.promptId == prompt?.id && state.check != null) "保存并识别" else "下一题")
                }
                OutlinedButton(onClick = { resetBoard(); viewModel.skip() }, enabled = prompt != null, modifier = noKeyFocus) {
                    Text("跳过")
                }
                TextButton(
                    onClick = { resetBoard(); viewModel.back() },
                    enabled = state.custom != null || state.index > 0,
                    modifier = noKeyFocus,
                ) { Text("上一题") }
                IconButton(
                    onClick = {
                        history.removeLastOrNull()?.let { previous ->
                            strokes.clear(); strokes.addAll(previous)
                            if (strokes.isEmpty()) sampleStartUptime = -1L
                        }
                    },
                    enabled = history.isNotEmpty(),
                    modifier = noKeyFocus,
                ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销") }
                TextButton(onClick = { resetBoard() }, enabled = strokes.isNotEmpty(), modifier = noKeyFocus) { Text("重写") }
                TextButton(modifier = noKeyFocus, onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
                    viewModel.pasteCustom(text)
                }) { Text("粘贴") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部样本？") },
            text = { Text("已保存的 ${state.totalSamples} 条样本会被删除，没导出的就找不回来了。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; viewModel.clearSamples() }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

/** 用本地 KaTeX（assets/katex）渲染公式；公式过宽时自动缩小字号。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KatexView(latex: String, modifier: Modifier = Modifier, color: Color? = null) {
    // 训练页出题和输入法手写公式预览共用；键盘里传键面文字颜色
    val textColor = (color ?: MaterialTheme.colorScheme.onSurface).toArgb()
    val colorCss = String.format("#%06X", 0xFFFFFF and textColor)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { web ->
            val key = "$colorCss|$latex"
            if (web.tag != key) {
                web.tag = key
                web.loadDataWithBaseURL(
                    "file:///android_asset/katex/",
                    katexHtml(latex, colorCss),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

private fun katexHtml(latex: String, color: String): String {
    val texJs = JSONObject.quote(latex)
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="stylesheet" href="katex.min.css"><script src="katex.min.js"></script>
        <style>
          html,body{margin:0;padding:0;background:transparent;color:$color;overflow:hidden}
          #f{position:fixed;left:0;top:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;white-space:nowrap}
          #m{font-size:26px}
          .katex-display{margin:0}
        </style></head><body><div id="f"><div id="m"></div></div><script>
          var m=document.getElementById('m');
          try{katex.render($texJs,m,{displayMode:true,throwOnError:false,strict:false});}
          catch(e){m.textContent=$texJs;}
          // 公式过宽/过高时整体缩小；视口尺寸可能稍后才确定，尺寸变化时重算
          function fit(){
            var w=innerWidth-8,h=innerHeight-4;
            if(w<=0||h<=0)return;
            m.style.fontSize='26px';
            var r=Math.min(1,w/m.scrollWidth,h/m.scrollHeight);
            m.style.fontSize=Math.max(8,Math.floor(26*r))+'px';
          }
          fit();addEventListener('resize',fit);
          if(document.fonts)document.fonts.ready.then(fit);
        </script></body></html>
    """.trimIndent()
}
