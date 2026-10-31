package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputMode
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.LanguageSwitchMode
import com.kingzcheung.xime.settings.LanguageSwitchOptions
import com.kingzcheung.xime.settings.LanguageSwitchPreferences
import com.kingzcheung.xime.settings.SchemaInfo
import com.kingzcheung.xime.settings.SchemaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LanguageKeySwitchSettings(
    schemas: List<SchemaInfo>,
    languageOrder: List<InputLanguage>,
    currentSchema: String,
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(LanguageSwitchPreferences.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        options = LanguageSwitchPreferences.read(context)
    }
    val currentNative = InputModes.languageOf(currentSchema, schemas)
    // 列出所有非英文语言（原来写死只有中文、日语，西班牙语等选不到）；此刻能选的只是已启用且方案已准备好的。
    val choices = LanguageSwitchPreferences.specificChoices(languageOrder)
    val nativeLanguages by produceState<List<InputLanguage>>(emptyList(), context, schemas, languageOrder) {
        value = withContext(Dispatchers.IO) {
            val available = schemas.filter {
                it.profile.mode == InputMode.KEYBOARD && SchemaManager.isSchemaCompiled(context, it.schemaId)
            }.map { it.profile.language }.toSet()
            choices.filter { it in available && it in languageOrder }
        }
    }
    fun save(value: LanguageSwitchOptions) {
        options = value
        LanguageSwitchPreferences.save(context, value)
    }
    val selectedLanguage = options.language?.takeIf { it in nativeLanguages }
        ?: currentNative.takeIf { it in nativeLanguages } ?: nativeLanguages.firstOrNull()
    Surface(modifier = Modifier.settingsSearchAnchor("语言键切换方式").testTag("language-key-switch-settings"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("语言键切换方式", style = MaterialTheme.typography.titleSmall)
            Text("屏幕键盘和物理浮条共用此设置；点按语言键切换，长按打开语言列表。物理键盘的 Shift 和 Ctrl + 空格快捷键保持各自功能。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                for ((mode, title) in listOf(
                    LanguageSwitchMode.CURRENT_ENGLISH to "当前语言与英文切换（默认）",
                    LanguageSwitchMode.CYCLE to "按已启用语言顺序循环切换",
                    LanguageSwitchMode.SPECIFIC_ENGLISH to "指定语言与英文切换",
                )) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("language-switch-mode:${mode.id}")
                        .selectable(options.mode == mode, role = Role.RadioButton) {
                            val selected = options.language?.takeIf { it in nativeLanguages }
                                ?: currentNative.takeIf { it in nativeLanguages } ?: nativeLanguages.firstOrNull()
                            save(options.copy(mode = mode, language = if (mode == LanguageSwitchMode.SPECIFIC_ENGLISH) selected else options.language))
                        }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = options.mode == mode, onClick = null)
                        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (options.mode == LanguageSwitchMode.SPECIFIC_ENGLISH) {
                if (nativeLanguages.isEmpty()) Text("先在上面打开一种语言（中文、日语、西班牙语等），等它的输入方案准备好后再选。",
                    style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup()) {
                    for (language in choices) {
                        val ready = language in nativeLanguages
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("language-switch-specific:${language.id}")
                            .selectable(selectedLanguage == language, enabled = ready, role = Role.RadioButton) {
                                save(options.copy(language = language))
                            }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedLanguage == language, onClick = null, enabled = ready)
                            Column(Modifier.weight(1f)) {
                                Text("${language.displayName} ↔ 英文", style = MaterialTheme.typography.bodyMedium,
                                    color = if (ready) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                if (!ready) Text(if (language in languageOrder) "${language.displayName}的输入方案还在准备"
                                    else "${language.displayName}未启用，在上面打开后可选",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
