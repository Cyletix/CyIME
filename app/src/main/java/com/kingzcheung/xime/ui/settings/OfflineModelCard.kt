package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.model.*
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import com.kingzcheung.xime.speech.SpeechModelSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Download and select in the existing speech settings; selection applies to the next recording. */
@Composable
internal fun OfflineModelCard() {
    val context = LocalContext.current
    val manager = remember { AsrModelManager(context) }
    val models by ModelManager.modelsFlow.collectAsState()
    val downloads by ModelManager.downloadStates.collectAsState()
    var selected by remember { mutableStateOf(manager.getSelectedModelId()) }
    val error = downloads.values.filterIsInstance<ModelDownloadState.Error>().firstOrNull()?.message
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelManager.loadFromRemote(context) } }
    val choices = SpeechModelSelection.primaryIds
    val firstPass = SpeechModelSelection.primary(selected)
    val refine = SpeechModelSelection.hasCorrection(selected)
    val senseReady = remember(downloads, models) {
        runCatching { manager.selection(SpeechModelCatalog.SENSEVOICE).ready }.getOrDefault(false)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("离线语音模型", style = MaterialTheme.typography.titleMedium)
            Text("选择一个主模型。切换对下一次录音生效；未下载的模型请先下载。", style = MaterialTheme.typography.bodyMedium)
            choices.forEach { id ->
                val info = manager.getAsrModels().firstOrNull { it.id == id }
                val state = downloads[id] as? ModelDownloadState.Downloading
                val ready = remember(id, downloads, models) { runCatching { manager.selection(id).ready }.getOrDefault(false) }
                val checked = firstPass == id
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().testTag("speech-choice:$id")
                    .selectable(selected = checked, enabled = ready, role = Role.RadioButton,
                        onClick = { manager.setFirstPassModel(id); selected = manager.getSelectedModelId() }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = checked, enabled = ready, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(SpeechModelSelection.displayName(id).orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text(info?.description.orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!ready && state == null) TextButton(onClick = {
                        ModelManager.getModel(id)?.let { ModelManager.downloadModelInBackground(context, it) }
                    }) { Text("下载") }
                }
                if (state != null) LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider()
            val canRefine = firstPass != SpeechModelCatalog.SENSEVOICE
            Row(Modifier.fillMaxWidth().testTag("speech-correction")
                .toggleable(value = refine, enabled = canRefine && (senseReady || refine), role = Role.Switch,
                    onValueChange = { manager.setRefinementEnabled(it); selected = manager.getSelectedModelId() }),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SenseVoice 二次校正", style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        !canRefine -> "SenseVoice 已作为主模型，无需再次校正。"
                        !senseReady -> "先下载上方的 SenseVoice 模型，再为主模型启用二次校正。"
                        else -> "由 SenseVoice 复核 Zipformer 或 Paraformer 的结果，会增加内存占用和等待时间。"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = refine, enabled = canRefine && (senseReady || refine), onCheckedChange = null)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
