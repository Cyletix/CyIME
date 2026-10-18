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

/**
 * 旧版本长按删除会把整条候选永久屏蔽（九键 t9-suppressed 列表、26 键 cyime-hidden 列表）。
 * 现在长按删除只忘掉学习记录；这里只剩下恢复以前屏蔽过的候选。
 */
@Composable
internal fun T9SuppressionPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // word -> true 为九键列表，false 为 26 键列表
    var words by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        try {
            words = withContext(Dispatchers.IO) {
                check(RimeConfigHelper.prepareEngine(context))
                val engine = RimeEngine.getInstance()
                engine.getSuppressedCandidates().map { it to true } + engine.getHiddenCandidates().map { it to false }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "暂时无法读取候选记录，请稍后重试。"
        } finally { busy = false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("以前屏蔽过的候选", style = MaterialTheme.typography.titleMedium)
        Text("旧版本长按删除会把整条候选藏起来；现在长按删除只忘掉学习记录，词库里的词会回到原位。这里可以把以前藏起来的候选放回去。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!busy && words.isEmpty() && error == null) Text("暂无屏蔽的候选")
        LazyColumn {
            items(words, key = { (word, t9) -> (if (t9) "t9:" else "26:") + word }) { (word, t9) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(word, modifier = Modifier.weight(1f))
                    Text(if (t9) "九键" else "26 键", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    val engine = RimeEngine.getInstance()
                                    if (t9) engine.restoreSuppressedCandidate(word) else engine.restoreHiddenCandidate(word)
                                }
                                if (restored) { words = words - (word to t9); error = null }
                                else error = "恢复失败，记录仍然保留，请重试。"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "恢复失败，记录仍然保留，请重试。"
                            } finally { busy = false }
                        }
                    }) { Text("放回去") }
                }
            }
        }
    }
}
