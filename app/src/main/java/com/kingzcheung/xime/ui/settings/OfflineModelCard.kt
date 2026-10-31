package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.model.*
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.speech.SpeechModelSelection
import com.kingzcheung.xime.speech.SpeechLanguages
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Three language mappings, one correction scope, and one model comparison at a time. */
@Composable
internal fun OfflineModelCard() {
    val context = LocalContext.current
    val manager = remember { AsrModelManager(context) }
    val models by ModelManager.modelsFlow.collectAsState()
    val downloads by ModelManager.downloadStates.collectAsState()
    val selected = remember { mutableStateMapOf<InputLanguage, String>().apply {
        InputLanguage.speech.forEach { put(it, manager.getSelectedModelId(it)) }
    } }
    var editing by remember { mutableStateOf<InputLanguage?>(null) }
    var ready by remember { mutableStateOf(emptySet<String>()) }
    var xasrReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelManager.loadFromRemote(context) } }
    LaunchedEffect(models, downloads) {
        ready = withContext(Dispatchers.IO) {
            SpeechModelSelection.primaryIds.filter { runCatching { manager.selection(it).ready }.getOrDefault(false) }.toSet()
        }
        xasrReady = withContext(Dispatchers.IO) { runCatching { manager.isXasrReady() }.getOrDefault(false) }
        // An unchosen SenseVoice default becomes Paraformer + SenseVoice once Paraformer is downloaded.
        InputLanguage.speech.forEach { selected[it] = withContext(Dispatchers.IO) { manager.getSelectedModelId(it) } }
    }
    fun refresh(language: InputLanguage) { selected[language] = manager.getSelectedModelId(language) }
    fun download(id: String) { ModelManager.getModel(id)?.let { ModelManager.downloadModelInBackground(context, it) } }
    val senseReady = SpeechModelCatalog.SENSEVOICE in ready
    var wholeSentence by remember { mutableStateOf(SettingsPreferences.isSttWholeSentence(context)) }
    // Every line below reads the same rule as the :asr service, so no two lines disagree about which model runs.
    fun pipeline(language: InputLanguage) = SpeechModelSelection.pipeline(selected.getValue(language),
        chinese = language == InputLanguage.CHINESE, wholeSentenceOn = wholeSentence, xasrReady = xasrReady)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(title = "语言 → 主模型") {
            InputLanguage.speech.forEachIndexed { index, language ->
                val mode = selected.getValue(language)
                val primary = SpeechModelSelection.primary(mode)
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Row(Modifier.fillMaxWidth().testTag("speech-language:${language.id}")
                    .clickable { editing = language }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${language.displayName} · ${SpeechModelSelection.displayName(primary).orEmpty()}",
                            style = MaterialTheme.typography.bodyLarge)
                        Text(buildString {
                            append(speechModelFacts(primary))
                            val pipe = pipeline(language)
                            if (pipe.finalizer == SpeechModelCatalog.XASR) append(" → X-ASR 整句定稿")
                            else if (SpeechModelSelection.hasCorrection(mode)) append(" → SenseVoice 校正")
                            if (pipe.wholeSentence && pipe.finalizer != SpeechModelCatalog.XASR) append(" · 整句定稿")
                            if (primary !in ready || SpeechModelSelection.hasCorrection(mode) && !senseReady) append(" · 待下载")
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "选择${language.displayName}模型")
                }
            }
        }
        SettingsSection(title = "二次校正") {
            // 与其他设置项一致：每种语言一行开关，两行都关即不校正。
            Column(Modifier.fillMaxWidth().testTag("speech-correction")) {
                listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH).forEachIndexed { index, language ->
                    val mode = selected.getValue(language)
                    val enabled = SpeechModelSelection.hasCorrection(mode)
                    val canRefine = SpeechModelSelection.supportsCorrection(mode)
                    val available = canRefine && (senseReady || enabled)
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth().testTag("speech-correction:${language.id}")
                        .toggleable(value = enabled, enabled = available, role = Role.Switch,
                            onValueChange = { manager.setRefinementEnabled(it, language); refresh(language) })
                        .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${language.displayName} SenseVoice 二次校正", style = MaterialTheme.typography.bodyLarge)
                            Text(when {
                                !canRefine -> "SenseVoice 已作为主模型，无需再次校正。"
                                !senseReady && !enabled -> "先下载 SenseVoice 模型，再为主模型启用二次校正。"
                                enabled && pipeline(language).finalizer == SpeechModelCatalog.XASR ->
                                    "停顿后复核主模型结果；整句定稿开着、X-ASR 已下载，现在由 X-ASR 代替 SenseVoice 定稿。"
                                else -> "停顿后由 SenseVoice 复核主模型结果，会增加内存占用和等待时间。"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, enabled = available, onCheckedChange = null, colors = settingsSwitchColors())
                    }
                }
                // 整句定稿只对中文生效：中文开着校正，或中文直接用 SenseVoice 时可用（英文的校正不受它影响）。
                val chineseMode = selected.getValue(InputLanguage.CHINESE)
                val chineseCorrection = SpeechModelSelection.hasCorrection(chineseMode)
                val senseVoiceChinese = SpeechModelSelection.primary(chineseMode) == SpeechModelCatalog.SENSEVOICE
                val wholeSentenceAvailable = SpeechModelSelection.wholeSentenceApplies(chineseMode, chinese = true)
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().settingsSearchAnchor("整句定稿").testTag("speech-whole-sentence")
                    .toggleable(value = wholeSentence, enabled = wholeSentenceAvailable, role = Role.Switch,
                        onValueChange = { wholeSentence = it; SettingsPreferences.setSttWholeSentence(context, it) })
                    .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("整句定稿", style = MaterialTheme.typography.bodyLarge)
                        Text(when {
                            chineseCorrection -> "中文说完一整句（停顿约 1 秒或点结束）再统一校正一次，保留标点，更准，说完要多等一会儿；关闭时每 6 秒校正一次。只对中文生效。"
                            senseVoiceChinese -> "中文用 SenseVoice 时保留标点，说完（点结束）把整段再识别一次，更准，说完要多等一会儿。只对中文生效。"
                            else -> "只对中文生效：先为中文开启上面的 SenseVoice 二次校正。"
                        },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 定稿模型写的是实际在用的那个：同一个 pipeline()，与上面主模型一行一致。
                        val finalizerNote = when {
                            pipeline(InputLanguage.CHINESE).finalizer == SpeechModelCatalog.XASR -> "中文整句定稿使用 X-ASR 模型。"
                            chineseCorrection && xasrReady -> "X-ASR 已下载；打开整句定稿后中文改用它定稿。"
                            chineseCorrection -> "中文整句定稿使用 SenseVoice；下载 X-ASR 模型（约 136 MB）后改用它，更准、更快。"
                            senseVoiceChinese -> "中文整句定稿使用 SenseVoice。X-ASR 只在中文主模型是 Paraformer 或 Zipformer、并开着二次校正时使用" +
                                if (xasrReady) "，已下载的 X-ASR 现在不用。" else "。"
                            else -> null
                        }
                        if (finalizerNote != null) Text(finalizerNote,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wholeSentence, enabled = wholeSentenceAvailable, onCheckedChange = null,
                        colors = settingsSwitchColors())
                }
                if (chineseCorrection && !xasrReady) {
                    ModelDownloadAction(SpeechModelCatalog.XASR, downloads) { download(SpeechModelCatalog.XASR) }
                }
                if (!senseReady) {
                    ModelDownloadAction(SpeechModelCatalog.SENSEVOICE, downloads) { download(SpeechModelCatalog.SENSEVOICE) }
                }
            }
        }
        Text("同一模型只存一份；运行时只保留当前主模型及可选校正，不同时加载三种语言。模型文件大小不等于运行内存。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("配置修改在下一次录音生效。录音中切换语言时，兼容同一模型就继续使用，否则保留已识别文字并切换模型，麦克风继续收音。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { language ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("${language.displayName}主模型") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                    SpeechModelSelection.primaryIds.filter { SpeechLanguages.supports(it, language) }.forEach { id ->
                        val checked = SpeechModelSelection.primary(selected.getValue(language)) == id
                        Row(Modifier.fillMaxWidth().testTag("speech-choice:${language.id}:$id")
                            .selectable(checked, enabled = id in ready, role = Role.RadioButton, onClick = {
                                manager.setFirstPassModel(id, language); refresh(language); editing = null
                            }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(checked, onClick = null, enabled = id in ready)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(SpeechModelSelection.displayName(id).orEmpty(), style = MaterialTheme.typography.bodyLarge)
                                Text(speechModelFacts(id), style = MaterialTheme.typography.bodySmall)
                                Text(if (id in ready) "已下载" else "未下载", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (id !in ready) ModelDownloadAction(id, downloads) { download(id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    }
                }
            }, confirmButton = { TextButton(onClick = { editing = null }) { Text("关闭") } })
    }
}

/** Factual file sizes and output timing; do not invent cross-device latency, RAM or accuracy scores. */
internal fun speechModelFacts(id: String): String = when (id) {
    SpeechModelCatalog.PARAFORMER -> "中英 · 边说边出字 · 226 MB"
    SpeechModelCatalog.SENSEVOICE -> "中英日粤韩 · 停顿出字 · 228 MB"
    SpeechModelCatalog.ZIPFORMER -> "中文 · 边说边出字 · 133 MB"
    SpeechModelCatalog.ZIPFORMER_JA_INT8 -> "日语 · 说话中先出预览 · 153 MB"
    SpeechModelCatalog.ZIPFORMER_JA -> "日语 · 说话中先出预览 · 586 MB"
    else -> ""
}

@Composable
private fun ModelDownloadAction(id: String, downloads: Map<String, ModelDownloadState>, onDownload: () -> Unit) {
    when (val state = downloads[id]) {
        is ModelDownloadState.Downloading -> {
            LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("下载中 ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        else -> {
            TextButton(onClick = onDownload) { Text(if (state is ModelDownloadState.Error) "重试下载" else "下载 ${speechModelFacts(id).substringAfterLast(" · ")}") }
            if (state is ModelDownloadState.Error) Text(state.message, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
