package com.kingzcheung.xime.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ai.AiClient
import com.kingzcheung.xime.ai.AiConfig
import com.kingzcheung.xime.ai.AiConfigStore
import com.kingzcheung.xime.ai.AiMessage
import com.kingzcheung.xime.ai.AiPresets
import com.kingzcheung.xime.ui.i18n.Text
import com.kingzcheung.xime.ui.i18n.UiToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置 › AI 助手：填 OpenAI 兼容接口的地址、密钥和模型，以及换设备用的同步码。
 *
 * 不填密钥时，键盘上的 AI 面板只用 DeepSeek 网页版取回复；填了才直接走这里的接口。
 * 密钥只存本机（[AiConfigStore] 里用 Keystore 加密），不进备份；同步码含密钥，只给用户自己复制。
 * 骨架、返回栏和配色跟「备份与恢复」「语音转文本」两页一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettingsContent(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { AiConfigStore.load(context) }
    // 表单直接编辑这几项；「保存」才写盘，「获取模型」只更新模型列表和模型名。
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var apiKey by remember { mutableStateOf(saved.apiKey) }
    var model by remember { mutableStateOf(saved.model) }
    var models by remember { mutableStateOf(saved.models) }
    var keyVisible by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testFailed by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    // 当前表单拼出的配置；服务商名字由地址反查，认不出来就是「自定义」。
    fun currentConfig() = AiConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        models = models,
        providerLabel = AiPresets.labelOf(baseUrl),
    )

    val provider = AiPresets.labelOf(baseUrl)
    // 同步码跟着表单实时变（含密钥，只在这一页由用户自己复制）。
    val syncCode = AiConfigStore.exportCode(currentConfig())

    // 保存到本机；保存后键盘面板重新打开就能用新配置。
    fun save() {
        AiConfigStore.save(context, currentConfig())
        UiToast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
    }

    // 获取模型：`GET {base}/models`，成功就把列表和选中的模型落盘，并弹出模型下拉；
    // 失败照接口回的原因提示。阻塞请求放 IO 线程，结果回主线程更新界面。
    fun fetchModels() {
        if (fetching) return
        fetching = true
        val config = currentConfig()
        scope.launch {
            val result = withContext(Dispatchers.IO) { AiClient.fetchModels(config) }
            fetching = false
            result.fold(
                onSuccess = { list ->
                    if (list.isNotEmpty()) {
                        models = list
                        if (model !in list) model = list.first()
                        AiConfigStore.saveModels(context, list, model)
                        modelMenu = true
                    }
                },
                onFailure = { error ->
                    UiToast.makeText(
                        context,
                        error.message?.takeIf { it.isNotBlank() } ?: "获取模型失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    // 测试连接：发一句「你好」，成功显示回复前 60 字，失败显示接口给的原因。
    fun testConnection() {
        if (testing) return
        testing = true
        testResult = null
        val config = currentConfig()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AiClient.chatOnce(config, listOf(AiMessage("user", "你好")))
            }
            testing = false
            testFailed = result.isFailure
            testResult = result.fold(
                onSuccess = { reply -> "连接成功：" + reply.trim().take(60) },
                onFailure = { error ->
                    "连接失败：" + (error.message?.takeIf { it.isNotBlank() } ?: "未知错误")
                },
            )
        }
    }

    // 复制同步码：只放到系统剪贴板，不上传。
    fun copyCode() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI 助手同步码", syncCode))
        UiToast.makeText(context, "已复制同步码", Toast.LENGTH_SHORT).show()
    }

    // 导入同步码：解析成功后填回表单，点「保存」才生效；格式不对只提示，不改当前配置。
    fun importCode() {
        val parsed = AiConfigStore.parseCode(importText)
        if (parsed == null) {
            UiToast.makeText(context, "同步码无效", Toast.LENGTH_SHORT).show()
            return
        }
        baseUrl = parsed.baseUrl
        apiKey = parsed.apiKey
        model = parsed.model
        models = parsed.models
        testResult = null
        UiToast.makeText(context, "已导入，点「保存」后生效", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .settingsVerticalScroll()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "不填密钥时，AI 助手只用 DeepSeek 网页版取回复；填了密钥才直接走下面的接口。密钥只存本机，不进备份、不上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // 服务商：一排预设，选一个就填好地址和默认模型；地址认不出来时高亮「自定义」。
            SettingsSection(title = "服务商") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiPresets.all.forEach { preset ->
                            FilterChip(
                                selected = provider == preset.label,
                                onClick = {
                                    if (preset.baseUrl.isBlank()) {
                                        // 「自定义」只是说明当前地址不是常见服务商，直接在下面填地址和模型。
                                        UiToast.makeText(context, "在下面直接填接口地址和模型", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // 换服务商后旧模型列表不再适用，清掉等「获取模型」重取。
                                        if (baseUrl != preset.baseUrl) models = emptyList()
                                        baseUrl = preset.baseUrl
                                        model = preset.model
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Text(
                        "选预设会填好接口地址和默认模型；也可以自己填别的 OpenAI 兼容接口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 接口配置：地址、密钥、模型，加上获取模型、测试连接和保存。
            SettingsSection(title = "接口配置") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("接口地址") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API 密钥") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(if (keyVisible) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 模型输入框和模型下拉共用一个锚点，获取成功后自动弹出。
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("模型") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = modelMenu,
                                onDismissRequest = { modelMenu = false }
                            ) {
                                models.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            model = name
                                            modelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { fetchModels() }, enabled = !fetching) {
                            if (fetching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("获取模型")
                            }
                        }
                    }
                    if (models.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已获取 ${models.size} 个可用模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { modelMenu = true }) { Text("选择模型") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = { save() }, modifier = Modifier.weight(1f)) {
                            Text("保存")
                        }
                        OutlinedButton(
                            onClick = { testConnection() },
                            enabled = !testing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (testing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("测试连接")
                            }
                        }
                    }
                    testResult?.let { result ->
                        Text(
                            result,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testFailed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "「测试连接」会往当前接口发一句「你好」，确认地址、密钥和模型都对；会产生少量费用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 同步码：换设备用，含密钥，只由用户自己复制。
            SettingsSection(title = "同步码") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = syncCode,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("当前配置的同步码") },
                        trailingIcon = {
                            TextButton(onClick = { copyCode() }) { Text("复制") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("粘贴同步码") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { importCode() }, enabled = importText.isNotBlank()) {
                        Text("导入")
                    }
                    Text(
                        "同步码含密钥，只在自己设备间使用，别发到群里。导入后点「保存」才生效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
