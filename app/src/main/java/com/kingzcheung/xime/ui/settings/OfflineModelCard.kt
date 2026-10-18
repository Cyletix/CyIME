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
import com.kingzcheung.xime.speech.DialectCorrection
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.speech.SpeechModelSelection
import com.kingzcheung.xime.speech.SpeechLanguages
import com.kingzcheung.xime.settings.InputLanguage
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
        InputLanguage.supported.forEach { put(it, manager.getSelectedModelId(it)) }
    } }
    var editing by remember { mutableStateOf<InputLanguage?>(null) }
    var choosingCorrector by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelManager.loadFromRemote(context) } }
    LaunchedEffect(models, downloads) {
        ready = withContext(Dispatchers.IO) {
            (SpeechModelSelection.primaryIds + DialectCorrection.correctorIds).distinct().filter { runCatching { manager.selection(it).ready }.getOrDefault(false) }.toSet()
        }
    }
    fun refresh(language: InputLanguage) { selected[language] = manager.getSelectedModelId(language) }
    fun download(id: String) { ModelManager.getModel(id)?.let { ModelManager.downloadModelInBackground(context, it) } }
    val senseReady = SpeechModelCatalog.SENSEVOICE in ready
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection(title = "语言 → 主模型") {
            InputLanguage.supported.forEachIndexed { index, language ->
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
                            val corrector = SpeechModelSelection.corrector(mode)
                            if (corrector != null) append(" → ${shortName(corrector)} 校正")
                            if (primary !in ready || corrector != null && corrector !in ready) append(" · 待下载")
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
                    val corrector = SpeechModelSelection.corrector(mode) ?: SpeechModelCatalog.SENSEVOICE
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
                            Text("${language.displayName} ${shortName(corrector)} 二次校正", style = MaterialTheme.typography.bodyLarge)
                            Text(when {
                                !canRefine -> "SenseVoice 已作为主模型，无需再次校正。"
                                !senseReady && !enabled -> "先下载 SenseVoice 模型，再为主模型启用二次校正。"
                                else -> "停顿后由 ${shortName(corrector)} 复核主模型结果，会增加内存占用和等待时间。"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, enabled = available, onCheckedChange = null)
                    }
                    // Chinese only: SenseVoice by default, or a dialect-robust pass to try out.
                    if (language == InputLanguage.CHINESE && canRefine) {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(Modifier.fillMaxWidth().testTag("speech-corrector:${language.id}")
                            .clickable { choosingCorrector = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("中文校正模型 · ${shortName(corrector)}", style = MaterialTheme.typography.bodyLarge)
                                Text("可改用方言模型；选中即开启中文二次校正。", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "选择中文校正模型")
                        }
                    }
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
    if (choosingCorrector) {
        val mode = selected.getValue(InputLanguage.CHINESE)
        AlertDialog(onDismissRequest = { choosingCorrector = false }, title = { Text("中文校正模型") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                    DialectCorrection.correctorIds.forEach { id ->
                        val checked = SpeechModelSelection.corrector(mode) == id
                        Row(Modifier.fillMaxWidth().testTag("speech-corrector-choice:$id")
                            .selectable(checked, enabled = id in ready, role = Role.RadioButton, onClick = {
                                manager.setModel(SpeechModelSelection.withCorrector(mode, id), InputLanguage.CHINESE)
                                refresh(InputLanguage.CHINESE); choosingCorrector = false
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
            }, confirmButton = { TextButton(onClick = { choosingCorrector = false }) { Text("关闭") } })
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
    SpeechModelCatalog.PARAFORMER -> "中英 · 流式 · 226 MB"
    SpeechModelCatalog.SENSEVOICE -> "中英日粤韩 · 停顿出字 · 228 MB"
    SpeechModelCatalog.ZIPFORMER -> "中文 · 流式 · 133 MB"
    SpeechModelCatalog.ZIPFORMER_JA_INT8 -> "日语 · 分段预览 · 153 MB"
    SpeechModelCatalog.ZIPFORMER_JA -> "日语 · 分段预览 · 586 MB"
    DialectCorrection.DOLPHIN -> "中文方言 · 无标点 · 104 MB"
    DialectCorrection.TELESPEECH -> "中文方言 · 无标点 · 341 MB"
    else -> ""
}

private fun shortName(id: String) = SpeechModelSelection.displayName(id)?.substringBefore(" ·") ?: id

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
