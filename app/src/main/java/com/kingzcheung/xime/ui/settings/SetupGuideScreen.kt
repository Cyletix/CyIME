package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.ClosedCaption
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Dialpad
import androidx.compose.material.icons.twotone.NetworkCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.model.DeviceModelTier
import com.kingzcheung.xime.model.SetupAnswers
import com.kingzcheung.xime.model.SetupFacts
import com.kingzcheung.xime.model.SetupGuide
import com.kingzcheung.xime.model.SetupGuidePlanner
import com.kingzcheung.xime.model.SetupItem
import com.kingzcheung.xime.model.SetupPlan
import com.kingzcheung.xime.model.SetupSource
import com.kingzcheung.xime.model.VoiceUse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 按设备推荐配置：首次设置完成后出现一次，之后从设置首页「配置选择」进入。
 * 先问三件用法（语音、九键、字幕），再按手机情况给出要留、要下载、可以删的模型，全部用白话说明；
 * 只有点了“按推荐设置”才改设置和删文件。
 */
@Composable
fun SetupGuideScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var facts by remember { mutableStateOf<SetupFacts?>(null) }
    var answers by remember { mutableStateOf<SetupAnswers?>(null) }
    var applying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SetupGuide.Result?>(null) }
    var allowMetered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val read = withContext(Dispatchers.IO) { SetupGuide.readFacts(context) }
        facts = read
        answers = SetupGuidePlanner.suggest(read)
    }
    val plan = remember(facts, answers) {
        val f = facts; val a = answers
        if (f != null && a != null) SetupGuidePlanner.plan(f, a) else null
    }
    // 默认全部勾上“删掉”；换了回答，方案变了就重新全选。
    var removeIds by remember(plan) { mutableStateOf(plan?.remove?.map { it.id }?.toSet().orEmpty()) }
    SetupGuideContent(
        facts = facts, plan = plan, removeIds = removeIds, allowMetered = allowMetered,
        applying = applying, result = result, firstRun = firstRun,
        onAnswers = { answers = it },
        onToggleRemove = { id, on -> removeIds = if (on) removeIds + id else removeIds - id },
        onAllowMetered = { allowMetered = it },
        onApply = {
            plan?.let { current ->
                applying = true
                scope.launch {
                    result = withContext(Dispatchers.IO) { SetupGuide.apply(context, current, removeIds, allowMetered) }
                    applying = false
                }
            }
        },
        onSkip = { SetupGuide.markShown(context); onDone() },
        onDone = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupGuideContent(
    facts: SetupFacts?,
    plan: SetupPlan?,
    removeIds: Set<String>,
    allowMetered: Boolean,
    applying: Boolean,
    result: SetupGuide.Result?,
    firstRun: Boolean,
    onAnswers: (SetupAnswers) -> Unit,
    onToggleRemove: (String, Boolean) -> Unit,
    onAllowMetered: (Boolean) -> Unit,
    onApply: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("按设备推荐配置") },
                navigationIcon = {
                    if (!firstRun) IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { if (firstRun && result == null) TextButton(onClick = onSkip) { Text("以后再说") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text("看看这台手机的情况，再告诉 CyIME 你打算怎么用，它会推荐留哪些模型、删掉哪些用不到的，让应用少占空间。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (facts == null || plan == null) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            } else if (result != null) {
                DeviceSection(facts)
                ResultSection(result, onDone)
                Spacer(Modifier.height(24.dp))
            } else {
            DeviceSection(facts)
            AnswerSection(plan.answers, onAnswers)
            KeepSection(plan.keep)
            if (plan.remove.isNotEmpty()) RemoveSection(plan.remove, removeIds, onToggleRemove)
            SummarySection(facts, plan, removeIds)
            if (facts.metered && facts.online && plan.downloadBytes > 0) SettingsSection(title = "下载") {
                SettingsToggleItem(icon = Icons.TwoTone.NetworkCell, title = "现在用流量下载",
                    subtitle = "要下载 ${SetupGuidePlanner.mbText(plan.downloadBytes)}；不打开就等连上 Wi-Fi 自动下载",
                    checked = allowMetered, onCheckedChange = onAllowMetered)
            }
            Button(onClick = onApply, enabled = !applying, modifier = Modifier.fillMaxWidth().testTag("setup-guide-apply")) {
                if (applying) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("正在整理…")
                } else Text("按推荐设置")
            }
            Text("以后想换，随时可以在 设置 → 配置选择 → 按设备推荐配置 重新选；删掉的模型在用到时会提示下载。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DeviceSection(facts: SetupFacts) {
    SettingsSection(title = "这台手机") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (facts.tier) {
                DeviceModelTier.ENHANCED -> "性能较强"
                DeviceModelTier.STANDARD -> "性能中等"
                DeviceModelTier.LIGHT -> "入门配置，优先省内存"
            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val hardware = buildList {
                facts.memoryBytes?.let { add("内存 ${(it + SetupGuidePlanner.GIB / 2) / SetupGuidePlanner.GIB} GB") }
                facts.cores?.let { add("$it 核") }
                facts.maxCpuKHz?.let { add("最高 ${"%.1f".format(it / 1_000_000.0)} GHz") }
            }
            if (hardware.isNotEmpty()) FactLine(hardware.joinToString("，"))
            facts.soc?.let { FactLine("处理器 $it") }
            facts.freeBytes?.let { FactLine("剩余空间 ${SetupGuidePlanner.gbText(it)}") }
            FactLine("网络 " + when { !facts.online -> "未联网"; facts.metered -> "移动数据"; else -> "Wi-Fi" })
            facts.language?.let { FactLine("系统语言 $it") }
        }
    }
}

@Composable
private fun FactLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AnswerSection(answers: SetupAnswers, onAnswers: (SetupAnswers) -> Unit) {
    SettingsSection(title = "语音输入打算怎么用") {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            listOf(
                Triple(VoiceUse.OFTEN, "经常用，想边说边看到字", "说话时字就跟着出来，停下后再自动改错、加标点"),
                Triple(VoiceUse.SOMETIMES, "偶尔用，说完出字就行", "说完一句再出字，错字少、带标点，占的空间少一半"),
                Triple(VoiceUse.NEVER, "不用语音", "关掉语音输入，不留语音模型（字幕要用的除外）"),
            ).forEachIndexed { index, (use, title, subtitle) ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().testTag("setup-voice:${use.name}")
                    .selectable(answers.voice == use, role = Role.RadioButton) { onAnswers(answers.copy(voice = use)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = answers.voice == use, onClick = null, modifier = Modifier.width(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    SettingsSection(title = "其他用法") {
        SettingsToggleItem(icon = Icons.TwoTone.Dialpad, title = "用九键打字",
            subtitle = "九键按整句常用说法组词，长句更容易一次打对；需要九键整句组词模型",
            checked = answers.t9, onCheckedChange = { onAnswers(answers.copy(t9 = it)) })
        HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SettingsToggleItem(icon = Icons.TwoTone.ClosedCaption, title = "用字幕",
            subtitle = "开会、和听力不好的人说话时，把别人说的话实时显示在屏幕上，并分清是谁说的",
            checked = answers.caption, onCheckedChange = { onAnswers(answers.copy(caption = it)) })
    }
}

@Composable
private fun KeepSection(items: List<SetupItem>) {
    SettingsSection(title = "推荐留下的") {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().testTag("setup-keep:${item.id}").padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.modelName.isNotEmpty()) Text("设置里叫「${item.modelName}」", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(12.dp))
                val size = SetupGuidePlanner.mbText(item.bytes)
                Text(when (item.source) {
                    SetupSource.ON_PHONE -> "已在手机上\n$size"
                    SetupSource.IN_PACKAGE -> "从安装包取出\n$size"
                    SetupSource.DOWNLOAD -> "要下载\n$size"
                }, style = MaterialTheme.typography.labelMedium,
                    color = if (item.source == SetupSource.DOWNLOAD) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun RemoveSection(items: List<SetupItem>, removeIds: Set<String>, onToggle: (String, Boolean) -> Unit) {
    SettingsSection(title = "可以删掉的") {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            val saving = SetupGuidePlanner.mbText(item.bytes)
            SettingsToggleItem(icon = Icons.TwoTone.Delete, title = item.title,
                subtitle = buildString {
                    append(item.reason)
                    append(if (item.source == SetupSource.ON_PHONE) "删掉省 $saving。" else "不从安装包解压，省 $saving。")
                    if (item.modelName.isNotEmpty()) append("设置里叫「${item.modelName}」。")
                },
                checked = item.id in removeIds, onCheckedChange = { onToggle(item.id, it) })
        }
    }
}

@Composable
private fun SummarySection(facts: SetupFacts, plan: SetupPlan, removeIds: Set<String>) {
    val now = facts.installed.values.sum()
    val removed = plan.remove.filter { it.id in removeIds && it.source == SetupSource.ON_PHONE }.sumOf { it.bytes }
    val added = plan.keep.filter { it.source != SetupSource.ON_PHONE }.sumOf { it.bytes }
    val after = now - removed + added
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("这些模型现在占 ${SetupGuidePlanner.mbText(now)}，整理后 ${SetupGuidePlanner.mbText(after)}" +
            (if (now > after) "，少占 ${SetupGuidePlanner.mbText(now - after)}。" else "。"),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        plan.notes.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultSection(result: SetupGuide.Result, onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✓ 已按推荐设置好", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            val lines = buildList {
                if (result.removed > 0) add("删掉了 ${result.removed} 个用不到的模型，空出 ${SetupGuidePlanner.mbText(result.freedBytes)}。")
                if (result.downloading > 0) add(if (result.waitingForWifi)
                    "还要下载 ${SetupGuidePlanner.mbText(result.downloading)}，连上 Wi-Fi 后自动下载。"
                    else "正在后台下载 ${SetupGuidePlanner.mbText(result.downloading)}，下好就能用。")
                if (result.failed.isNotEmpty()) add("这些没删成：${result.failed.joinToString("、")}，可以在 扩展 → 已安装的模型 里再删。")
            }
            lines.forEach { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
}
