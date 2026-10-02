package com.kingzcheung.xime.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.ChineseSchemas
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class SetupStatus(
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val preparing: Boolean = true,
    val schemas: List<String> = emptyList(),
    val error: String? = null,
) {
    val ready: Boolean get() = !preparing && error == null && schemas.isNotEmpty()
    val complete: Boolean get() = imeEnabled && imeSelected && ready
}

@Composable
fun SetupWizardScreen(
    visible: Boolean = true,
    onNavigateToSchemaSettings: () -> Unit,
    onCompleted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var status by remember { mutableStateOf(SetupStatus()) }
    var attempt by remember { mutableIntStateOf(0) }

    fun refreshImeState() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val selected = ComponentName.unflattenFromString(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
        status = status.copy(
            imeEnabled = imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true,
            imeSelected = selected?.packageName == context.packageName,
        )
    }
    LaunchedEffect(visible, lifecycle) {
        if (visible) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // The system picker can close without a new activity resume event.
            while (true) { refreshImeState(); delay(500) }
        }
    }
    LaunchedEffect(visible, attempt) {
        if (!visible) return@LaunchedEffect
        status = status.copy(preparing = true, error = null)
        try {
            val schemas = withContext(Dispatchers.IO) {
                RimeConfigHelper.prepareAutomatically(context)
                SchemaManager.getEnabledSchemas(context).map { ChineseSchemas.displayName(it, it) }
            }
            status = status.copy(preparing = false, schemas = schemas,
                error = if (schemas.isEmpty()) "请至少启用一个输入方案" else null)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            Log.e("SetupWizard", "Automatic preparation failed", e)
            status = status.copy(preparing = false, error = "词库准备失败，请重试")
        }
    }
    if (visible) SetupWizardContent(
        status = status,
        onEnable = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
        onSchemas = onNavigateToSchemaSettings,
        onRetry = { attempt++ },
        onSwitch = { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker() },
        onFinish = {
            refreshImeState()
            if (status.complete) {
                val enabled = SchemaManager.getEnabledSchemas(context)
                if (enabled.isNotEmpty()) {
                    if (SettingsPreferences.getCurrentSchema(context) !in enabled)
                        SettingsPreferences.setCurrentSchema(context, enabled.first())
                    SettingsPreferences.setSetupCompleted(context, true)
                    onCompleted()
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupWizardContent(
    status: SetupStatus,
    onEnable: () -> Unit,
    onSchemas: () -> Unit,
    onRetry: () -> Unit,
    onSwitch: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("设置向导") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("按顺序完成下面三项，即可开始输入。", style = MaterialTheme.typography.bodyLarge)
            SetupCard(1, "启用 CyIME", status.imeEnabled,
                if (status.imeEnabled) "已启用" else "待启用") {
                if (!status.imeEnabled) {
                    Text("在系统输入法列表中打开 CyIME，返回后会自动检查。")
                    Button(onClick = onEnable) { Text("去系统设置启用") }
                }
            }
            SetupCard(2, "准备输入方案", status.ready,
                when { status.preparing -> "准备中"; status.error != null -> "需要处理"; status.ready -> "已就绪"; else -> "待准备" }) {
                when {
                    status.preparing -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("正在准备词库，首次使用可能需要几分钟…")
                    }
                    status.error != null -> {
                        Text(status.error, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRetry) { Text("重试准备") }
                    }
                    else -> Text(status.schemas.joinToString(" · "))
                }
                TextButton(onClick = onSchemas, enabled = !status.preparing) { Text("选择其他方案（可选）") }
            }
            SetupCard(3, "切换到 CyIME", status.imeEnabled && status.imeSelected,
                if (status.imeEnabled && status.imeSelected) "已切换" else "待切换") {
                if (!status.imeEnabled || !status.imeSelected) {
                    Text("启用并准备完成后，在输入法选择器中选中 CyIME。")
                    Button(onClick = onSwitch, enabled = status.imeEnabled && status.ready) { Text("选择 CyIME") }
                }
            }
            if (status.complete) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("✓ 设置成功，可以开始输入了", Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            Button(onClick = onFinish, enabled = status.complete, modifier = Modifier.fillMaxWidth()) {
                Text(if (status.complete) "开始使用" else "完成三项后即可开始使用")
            }
        }
    }
}

@Composable
private fun SetupCard(number: Int, title: String, complete: Boolean, state: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (complete)
        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (complete) "✓" else number.toString(), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(state, style = MaterialTheme.typography.labelLarge)
            }
            content()
        }
    }
}
