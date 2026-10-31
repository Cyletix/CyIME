package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilityProbe
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.settings.PerformanceProfile
import com.kingzcheung.xime.settings.PerformanceSettings
import kotlin.math.roundToInt

internal val tierNames = mapOf(PerformanceTier.HIGH to "高配", PerformanceTier.MEDIUM to "中配", PerformanceTier.LOW to "低配")
private val tierHints = mapOf(
    PerformanceTier.HIGH to "动效和炫光全开，语音模型常驻，二次校正模型空闲 30 分钟后释放",
    PerformanceTier.MEDIUM to "保留按键动效，语音空闲 5 分钟先释放二次校正模型，30 分钟后全部释放",
    PerformanceTier.LOW to "关闭动效和炫光，语音用完 1 分钟就释放，内存最省",
)
private fun idleLabel(minutes: Int) = if (minutes == 0) "永不" else "$minutes 分钟"

private val ladder = listOf(PerformanceTier.LOW, PerformanceTier.MEDIUM, PerformanceTier.HIGH)

/** 「配置高低」标题后那句：几分，落在哪一档或哪两档之间。 */
internal fun positionText(settings: PerformanceSettings): String {
    val score = PerformanceProfile.score(settings)
    PerformanceProfile.matching(settings)?.let { return "$score 分，就是${tierNames.getValue(it)}" }
    val ticks = ladder.map { it to PerformanceProfile.score(PerformanceProfile.presets.getValue(it)) }
    ticks.firstOrNull { it.second == score }?.let { return "$score 分，和${tierNames.getValue(it.first)}一样高" }
    val lower = ticks.lastOrNull { it.second < score }
    val upper = ticks.firstOrNull { it.second > score }
    return when {
        lower == null -> "$score 分，比低配还省"
        upper == null -> "$score 分，比高配还高"
        else -> "$score 分，在${tierNames.getValue(lower.first)}和${tierNames.getValue(upper.first)}之间"
    }
}

/**
 * 性能与省电：一键选档；改任何一项自动变成“自定义”。
 * 和「按设备推荐配置」分工：这页随时调、马上生效（动效、语音模型常驻多久）；用哪些模型、删哪些在那一页，
 * 那里要下载、删文件，点了「按推荐设置」才改。两页互相有入口，设备档位用同一个判断。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsContent(onBack: () -> Unit, onOpenSetupGuide: (() -> Unit)? = null) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(PerformanceProfile.read(context)) }
    var auto by remember { mutableStateOf(PerformanceProfile.isAuto(context)) }
    val device = remember { DeviceCapabilityProbe.read(context) }
    val detected = remember { PerformanceProfile.detectedTier(context) }
    val current = PerformanceProfile.matching(settings)
    fun refresh() { settings = PerformanceProfile.read(context); auto = PerformanceProfile.isAuto(context) }
    fun custom(next: PerformanceSettings) { PerformanceProfile.applyCustom(context, next); refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("性能与省电") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { padding ->
        val listState = rememberSettingsSearchListState()
        LazyColumn(modifier = Modifier.settingsGutterScroll(listState).fillMaxSize().padding(padding).padding(horizontal = 16.dp), state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("本机：${describe(device)} · 本机配置推荐：${tierNames.getValue(detected)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            item {
                SettingsSection(title = "档位") {
                    PerformanceScale(settings)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TierRow("自动（推荐）", "按本机配置自动选档 · 本机配置推荐：${tierNames.getValue(detected)}", auto) {
                        PerformanceProfile.applyAuto(context); refresh()
                    }
                    listOf(PerformanceTier.HIGH, PerformanceTier.MEDIUM, PerformanceTier.LOW).forEach { tier ->
                        RowDivider()
                        TierRow(tierNames.getValue(tier), tierHints.getValue(tier), !auto && current == tier) {
                            PerformanceProfile.applyPreset(context, tier); refresh()
                        }
                    }
                    // 「自定义」一直列着但不能点：手动改过明细或动效、值对不上任何一档时自动选中，表示当前状态。
                    val isCustom = !auto && current == null
                    RowDivider()
                    TierRow("自定义", if (isCustom) "已单独调整明细或「按键效果」里的动效、炫光"
                        else "单独调整下面的明细或「按键效果」里的动效、炫光后，自动选中这一项", isCustom, enabled = false) {}
                }
            }
            item {
                SettingsSection(title = "明细") {
                    // 分档横条，与「输入体验」里长按时长等设置同一样式：标题写当前值，下面一行说明，再是分档滑块。
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IdleLevelSlider("语音二次校正空闲后释放", settings.speechRefinerIdleMinutes,
                            "边说边出字＋二次校正时，先卸掉校正模型（SenseVoice 或 X-ASR），保留出字模型；再用时第一句定稿稍慢。只用一个模型（如只用 SenseVoice）时不起作用",
                            "speech-refiner-idle-slider") { custom(settings.copy(speechRefinerIdleMinutes = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        IdleLevelSlider("语音模型空闲后全部释放", settings.speechEngineIdleMinutes,
                            "“永不”= 一直常驻，按麦克风最快，占内存最多",
                            "speech-engine-idle-slider") { custom(settings.copy(speechEngineIdleMinutes = it)) }
                    }
                }
            }
            onOpenSetupGuide?.let { open ->
                item {
                    SettingsSection(title = "模型") {
                        SettingsItem(icon = Icons.TwoTone.Tune, title = "按设备推荐配置",
                            subtitle = "这页只管动效和语音模型常驻多久；语音、九键、联想用哪些模型，删掉用不到的，在这里选",
                            onClick = open, showArrow = true)
                    }
                }
            }
            item {
                Text("语音设置在下次开始说话时生效。字幕模式开着时不会释放语音模型。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

private fun describe(device: DeviceCapabilities): String = buildList {
    device.totalMemoryBytes?.takeIf { it > 0 }?.let { add("${Math.round(it.toDouble() / DeviceCapabilities.GIB)} GB 内存") }
    device.processors?.let { add("$it 核") }
    // 模拟器等读到的值可能不是真实频率，低于 0.5 GHz 不显示。
    device.maxCpuKHz?.takeIf { it >= 500_000 }?.let { add("最高 ${"%.1f".format(it / 1_000_000.0)} GHz") }
}.ifEmpty { listOf("配置未知") }.joinToString(" · ")

@Composable
internal fun RowDivider() = HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
internal fun TierRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            RadioButton(selected = selected, onClick = null, enabled = enabled || selected)
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val idleLevels = PerformanceProfile.idleLevels

@Composable
private fun IdleLevelSlider(title: String, minutes: Int, description: String, tag: String, onChoose: (Int) -> Unit) {
    // 旧设置里可能有不在档位上的值：落到最接近的档显示，拖动后才改。
    val index = PerformanceProfile.idleLevelIndex(minutes)
    var position by remember(minutes) { mutableStateOf(index.toFloat()) }
    val shown = idleLevels[position.roundToInt().coerceIn(idleLevels.indices)]
    // 和「按键效果」里的滑条同一写法：标题 + 主题色当前值一行，说明，滑条；两端标出最短和“永不”。
    Column(Modifier.settingsSearchAnchor(title, bleed = 16.dp, bleedVertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(idleLabel(shown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Slider(value = position, onValueChange = { position = it }, modifier = Modifier.testTag(tag),
            onValueChangeFinished = { if (shown != minutes) onChoose(shown) },
            valueRange = 0f..(idleLevels.size - 1).toFloat(), steps = idleLevels.size - 2)
        Row(Modifier.fillMaxWidth()) {
            Text(idleLabel(idleLevels.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(idleLabel(idleLevels.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 刻度条两端各留半个圆点宽，0 分和 100 分的圆点不出界；文字按同一位置对齐。 */
private val scaleInset = 10.dp

/**
 * 档位卡片顶上的刻度条：标题写几分、落在哪里，下面一条和滑条同色的横条，低配、中配、高配三档按同一算法标刻度，
 * 实心圆点是当前设置。选中某档时圆点正好压在那档的刻度上；自定义时落在两档之间。
 */
@Composable
private fun PerformanceScale(settings: PerformanceSettings) {
    val colors = MaterialTheme.colorScheme
    val score = PerformanceProfile.score(settings)
    val current = PerformanceProfile.matching(settings)
    val ticks = ladder.map { it to PerformanceProfile.score(PerformanceProfile.presets.getValue(it)) }
    Column(Modifier.fillMaxWidth().testTag("performance-scale").padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("配置高低", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(positionText(settings), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        }
        Text("0 最省，100 全开：语音模型常驻时长占 70 分，按键动效、炫光各 15 分。分越高，按麦克风越快，占内存和电越多",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        ScaleLabels(listOf(score)) {
            Text("当前", style = MaterialTheme.typography.labelSmall, color = colors.primary, fontWeight = FontWeight.Medium)
        }
        val inactive = colors.secondaryContainer
        val tickIdle = colors.outline
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val inset = scaleInset.toPx()
            val y = size.height / 2
            fun x(points: Int) = inset + (size.width - 2 * inset) * points / 100f
            val line = 4.dp.toPx()
            drawLine(inactive, Offset(x(0), y), Offset(x(100), y), strokeWidth = line, cap = StrokeCap.Round)
            if (score > 0) drawLine(colors.primary, Offset(x(0), y), Offset(x(score), y), strokeWidth = line, cap = StrokeCap.Round)
            ticks.forEach { (_, points) -> drawCircle(if (points <= score) colors.primary else tickIdle, 4.dp.toPx(), Offset(x(points), y)) }
            drawCircle(colors.primary, 9.dp.toPx(), Offset(x(score), y))
            drawCircle(colors.onPrimary, 3.5.dp.toPx(), Offset(x(score), y))
        }
        ScaleLabels(ticks.map { it.second }) {
            ticks.forEach { (tier, points) ->
                val here = tier == current
                Text("${tierNames.getValue(tier)} $points", style = MaterialTheme.typography.labelSmall,
                    color = if (here) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** 一行文字，每个按 [points]（0～100）对准刻度条上的位置居中，靠边时收在行内。 */
@Composable
private fun ScaleLabels(points: List<Int>, inset: Dp = scaleInset, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val start = inset.roundToPx()
        val track = width - 2 * start
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { index, placeable ->
                val center = start + track * points[index] / 100f
                placeable.placeRelative((center - placeable.width / 2f).roundToInt().coerceIn(0, maxOf(0, width - placeable.width)), 0)
            }
        }
    }
}
