package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Persistent negative feedback is editable independently of learned words. */
@Composable
internal fun T9SuppressionPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        try {
            words = withContext(Dispatchers.IO) {
                check(RimeConfigHelper.prepareEngine(context))
                RimeEngine.getInstance().getSuppressedCandidates()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "暂时无法读取候选记录，请稍后重试。"
        } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("中文九键 · 不再推荐", style = MaterialTheme.typography.titleMedium)
        Text("只屏蔽完整候选，不删除它包含的字词。恢复后会重新参与正常排序。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!busy && words.isEmpty() && error == null) Text("暂无屏蔽的候选")
        LazyColumn {
            items(words, key = { it }) { word ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(word, modifier = Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    RimeEngine.getInstance().restoreSuppressedCandidate(word)
                                }
                                if (restored) { words = words - word; error = null }
                                else error = "恢复失败，记录仍然保留，请重试。"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "恢复失败，记录仍然保留，请重试。"
                            } finally { busy = false }
                        }
                    }) { Text("恢复推荐") }
                }
            }
        }
    }
}
