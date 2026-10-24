package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.DictionaryHit
import com.kingzcheung.xime.settings.DictionaryLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 查词：一个词在内置词库、导入的词库、我的短语里有没有，各是什么编码。 */
@Composable
internal fun DictionaryLookupPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var word by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, List<DictionaryHit>>?>(null) }
    fun search() {
        val target = word.trim()
        if (target.isEmpty() || searching) return
        searching = true
        scope.launch {
            val hits = withContext(Dispatchers.IO) { runCatching { DictionaryLookup.find(context, target) }.getOrDefault(emptyList()) }
            result = target to hits
            searching = false
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("输入一个词，看它在内置词库、导入的词库和我的短语里有没有、编码是什么。打字时学会的词不在这里显示。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("词") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }), modifier = Modifier.fillMaxWidth())
        Button(onClick = ::search, enabled = word.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) {
            Text("查词")
        }
        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        result?.let { (target, hits) ->
            if (hits.isEmpty()) Text("“$target”不在内置词库、导入的词库和我的短语里。")
            hits.forEach { hit ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(hit.source, style = MaterialTheme.typography.titleSmall)
                        Text(hit.codes.joinToString("；"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
