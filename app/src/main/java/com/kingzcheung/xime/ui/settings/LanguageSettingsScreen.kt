package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kingzcheung.xime.settings.*
import com.kingzcheung.xime.ui.menubar.reorderOnLongPress
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var languages by remember { mutableStateOf(LanguagePreferences.enabled(context)) }
    var orderedLanguages by remember { mutableStateOf(InputModes.languageOrder(context)) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val cardCenters = remember { mutableStateMapOf<InputLanguage, Float>() }
    var dragging by remember { mutableStateOf<InputLanguage?>(null) }
    var dropTarget by remember { mutableStateOf<InputLanguage?>(null) }
    var draggedOrder by remember { mutableStateOf<List<InputLanguage>?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragStartCenters by remember { mutableStateOf<Map<InputLanguage, Float>>(emptyMap()) }
    var dragStartOrder by remember { mutableStateOf<List<InputLanguage>>(emptyList()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        languages = LanguagePreferences.enabled(context)
        orderedLanguages = InputModes.languageOrder(context)
    }
    Scaffold(modifier = Modifier.testTag("language-settings"), containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text("语言管理") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("英文始终开启；中文和日语可按需开启。长按已开启语言卡片拖动排序，与键盘中的语言顺序同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            (orderedLanguages + InputLanguage.entries.filterNot { it in languages }).forEach { language -> key(language.id) {
                val checked = language in languages
                val canChange = language != InputLanguage.ENGLISH && !saving
                Surface(modifier = Modifier.fillMaxWidth().testTag("language-drag:${language.id}")
                    .onGloballyPositioned { cardCenters[language] = it.positionInRoot().y + it.size.height / 2f }
                    .then(if (checked && orderedLanguages.size > 1 && !saving) Modifier.reorderOnLongPress(
                        onStart = {
                            dragging = language
                            dropTarget = null
                            draggedOrder = orderedLanguages
                            dragDistance = 0f
                            dragStartOrder = orderedLanguages
                            dragStartCenters = orderedLanguages.mapNotNull { item ->
                                cardCenters[item]?.let { item to it }
                            }.toMap()
                        },
                        onDrag = { dy ->
                            if (dragging == language) {
                                dragDistance += dy
                                val center = dragStartCenters[language]
                                if (center != null) {
                                    val from = dragStartOrder.indexOf(language)
                                    if (from >= 0) {
                                        val movingCenter = center + dragDistance
                                        var target = from
                                        if (dragDistance > 0f) {
                                            for (index in from + 1..dragStartOrder.lastIndex) {
                                                val previous = dragStartCenters[dragStartOrder[index - 1]] ?: break
                                                val next = dragStartCenters[dragStartOrder[index]] ?: break
                                                if (movingCenter >= (previous + next) / 2f) target = index else break
                                            }
                                        } else if (dragDistance < 0f) {
                                            for (index in from - 1 downTo 0) {
                                                val previous = dragStartCenters[dragStartOrder[index]] ?: break
                                                val next = dragStartCenters[dragStartOrder[index + 1]] ?: break
                                                if (movingCenter <= (previous + next) / 2f) target = index else break
                                            }
                                        }
                                        dropTarget = dragStartOrder.getOrNull(target)?.takeIf { target != from }
                                        draggedOrder = dragStartOrder.toMutableList().apply { add(target, removeAt(from)) }
                                    }
                                }
                            }
                        },
                        onEnd = {
                            if (dragging == language) {
                                val nextOrder = draggedOrder
                                if (nextOrder != null && nextOrder != dragStartOrder) {
                                    InputModes.saveLanguageOrder(context, nextOrder.map { it.id })
                                    orderedLanguages = InputModes.languageOrder(context)
                                }
                                dragging = null
                                dropTarget = null
                                draggedOrder = null
                            }
                        },
                        onCancel = {
                            if (dragging == language) {
                                dragging = null
                                dropTarget = null
                                draggedOrder = null
                            }
                        },
                    ) else Modifier),
                    shape = RoundedCornerShape(12.dp),
                    border = when (language) {
                        dragging -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        dropTarget -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                        else -> null
                    },
                    color = if (dragging == language) MaterialTheme.colorScheme.primaryContainer
                        else if (dropTarget == language) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().testTag("language-${language.id}")
                        .visualMaterial(VisualStyles.current, 12.dp, level = MaterialLevel.RAISED)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .toggleable(checked, enabled = canChange, role = Role.Switch) { selected ->
                                scope.launch {
                                    saving = true
                                    try {
                                        withContext(Dispatchers.IO) { LanguagePreferences.save(context, language, selected) }
                                        languages = LanguagePreferences.enabled(context)
                                        orderedLanguages = InputModes.languageOrder(context)
                                        message = if (selected) "已开启，输入方案会在后台自动准备" else "已关闭，已下载词库保留"
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (error: Exception) { message = error.message ?: "保存失败，请稍后再试" }
                                    finally { saving = false }
                                }
                            }.testTag("language-toggle:${language.id}").padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(when (language) {
                                    InputLanguage.CHINESE -> "按需开启 · 26键、九键、手写"
                                    InputLanguage.JAPANESE -> "按需开启 · 罗马字、假名"
                                    InputLanguage.ENGLISH -> "始终开启 · 英文键盘"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked, onCheckedChange = null, enabled = canChange)
                        }
                    }
                }
            } }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}
