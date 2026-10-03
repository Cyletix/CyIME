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
    val nativeLanguages by produceState<List<InputLanguage>>(emptyList(), context, schemas, languageOrder) {
        value = withContext(Dispatchers.IO) {
            val available = schemas.filter {
                it.profile.mode == InputMode.KEYBOARD && SchemaManager.isSchemaCompiled(context, it.schemaId)
            }.map { it.profile.language }.toSet()
            languageOrder.filter {
                it in available && it in listOf(InputLanguage.CHINESE, InputLanguage.JAPANESE)
            }
        }
    }
    fun save(value: LanguageSwitchOptions) {
        options = value
        LanguageSwitchPreferences.save(context, value)
    }
    val selectedLanguage = options.language?.takeIf { it in nativeLanguages }
        ?: currentNative.takeIf { it in nativeLanguages } ?: nativeLanguages.firstOrNull()
    Surface(modifier = Modifier.testTag("language-key-switch-settings"), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
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
                if (nativeLanguages.isEmpty()) Text("请先在语言设置中启用并准备中文或日语输入资源。",
                    style = MaterialTheme.typography.bodyMedium)
                else Column(Modifier.selectableGroup()) {
                    for (language in nativeLanguages) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("language-switch-specific:${language.id}")
                            .selectable(selectedLanguage == language, role = Role.RadioButton) {
                                save(options.copy(language = language))
                            }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedLanguage == language, onClick = null)
                            Text("${language.displayName} ↔ 英文", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
