package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Download and select in the existing speech settings; selection applies to the next recording. */
@Composable
internal fun OfflineModelCard() {
    val context = LocalContext.current
    val manager = remember { AsrModelManager(context) }
    val scope = rememberCoroutineScope()
    val models by ModelManager.modelsFlow.collectAsState()
    val downloads by ModelManager.downloadStates.collectAsState()
    var selected by remember { mutableStateOf(manager.getSelectedModelId()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelManager.loadFromRemote(context) } }
    val choices = remember(models) { manager.getAsrModels().map { it.id } + SpeechModelCatalog.TWO_PASS }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("离线语音模型", style = MaterialTheme.typography.titleMedium)
            Text("按停顿分段，标点转为空格。切换对下一次录音生效。", style = MaterialTheme.typography.bodyMedium)
            choices.forEach { id ->
                val info = manager.getAsrModels().firstOrNull { it.id == id }
                val ids = if (id == SpeechModelCatalog.TWO_PASS)
                    listOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE) else listOf(id)
                val state = ids.mapNotNull { downloads[it] }.filterIsInstance<ModelDownloadState.Downloading>().firstOrNull()
                val ready = remember(id, downloads, models) { runCatching { manager.selection(id).ready }.getOrDefault(false) }
                val title = info?.name ?: "Paraformer + SenseVoice 双模型"
                val detail = info?.description ?: "中英流式预览，多语第二遍校正；约 455 MB，日语需等定稿，内存与耗电高于单模型。"
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().selectable(selected = selected == id, enabled = ready, role = Role.RadioButton,
                    onClick = { manager.setModel(id); selected = id }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == id, enabled = ready, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!ready && state == null) TextButton(onClick = {
                        error = null
                        scope.launch {
                            for (required in ids) {
                                if (manager.selection(required).ready) continue
                                val fallback = AsrModelManager.DEFAULT_MODEL
                                val model = ModelManager.getModel(required) ?: ModelInfo(fallback.id, fallback.name,
                                    fallback.description, ModelCategory.ASR, versions = listOf(ModelVersion(
                                        version = "2025-06-30", size = fallback.size, archiveUrl = fallback.downloadUrl,
                                        files = fallback.files.map { ModelFile(it, "") })))
                                var failed = false
                                ModelManager.downloadModel(context, model, { result ->
                                    if (result is ModelDownloadState.Error) { error = result.message; failed = true }
                                })
                                if (failed) break
                            }
                        }
                    }) { Text("下载") }
                }
                if (state != null) LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
