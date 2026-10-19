package com.kingzcheung.xime.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilityProbe
import com.kingzcheung.xime.runtime.adaptation.PerformanceTier
import com.kingzcheung.xime.settings.PerformanceProfile
import com.kingzcheung.xime.settings.PerformanceSettings
import kotlin.math.roundToInt

private val tierNames = mapOf(PerformanceTier.HIGH to "高配", PerformanceTier.MEDIUM to "中配", PerformanceTier.LOW to "低配")
private val tierHints = mapOf(
    PerformanceTier.HIGH to "动效和炫光全开，语音 Paraformer 常驻，SenseVoice 空闲 30 分钟后释放",
    PerformanceTier.MEDIUM to "保留按键动效，语音空闲 5 分钟先释放 SenseVoice，30 分钟后全部释放",
    PerformanceTier.LOW to "关闭动效和炫光，语音用完 1 分钟就释放，内存最省",
)
private fun idleLabel(minutes: Int) = if (minutes == 0) "永不" else "$minutes 分钟"

/** 性能与省电：一键选档；改任何一项自动变成“自定义”。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsContent(onBack: () -> Unit) {
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
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("本机：${describe(device)} · 本机配置推荐：${tierNames.getValue(detected)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            item {
                SettingsSection(title = "档位") {
                    TierRow("自动（推荐）", "按本机配置自动选档 · 本机配置推荐：${tierNames.getValue(detected)}", auto) {
                        PerformanceProfile.applyAuto(context); refresh()
                    }
                    listOf(PerformanceTier.HIGH, PerformanceTier.MEDIUM, PerformanceTier.LOW).forEach { tier ->
                        RowDivider()
                        TierRow(tierNames.getValue(tier), tierHints.getValue(tier), !auto && current == tier) {
                            PerformanceProfile.applyPreset(context, tier); refresh()
                        }
                    }
                    // 「自定义」不是可点的档位：只有手动改过明细或动效时才显示，表示当前状态。
                    if (!auto && current == null) {
                        RowDivider()
                        TierRow("自定义", "已单独调整明细或「按键效果」里的动效、炫光", true, enabled = false) {}
                    }
                }
            }
            item {
                SettingsSection(title = "明细") {
                    // 分档横条，与「输入体验」里长按时长等设置同一样式：标题写当前值，下面一行说明，再是分档滑块。
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IdleLevelSlider("语音二次校正空闲后释放", settings.speechRefinerIdleMinutes,
                            "SenseVoice 不用时卸掉，保留 Paraformer 边说边出字；再用时第一句定稿稍慢",
                            "speech-refiner-idle-slider") { custom(settings.copy(speechRefinerIdleMinutes = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        IdleLevelSlider("语音模型空闲后全部释放", settings.speechEngineIdleMinutes,
                            "“永不”= 一直常驻，按麦克风最快，占内存最多",
                            "speech-engine-idle-slider") { custom(settings.copy(speechEngineIdleMinutes = it)) }
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
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun TierRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
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

/** 空闲释放时间的几个档：1、5、10、30、60 分钟和“永不”（0）。 */
private val idleLevels = listOf(1, 5, 10, 30, 60, 0)

@Composable
private fun IdleLevelSlider(title: String, minutes: Int, description: String, tag: String, onChoose: (Int) -> Unit) {
    // 旧设置里可能有不在档位上的值：落到最接近的档显示，拖动后才改。
    val index = idleLevels.indexOf(minutes).takeIf { it >= 0 }
        ?: idleLevels.indices.filter { idleLevels[it] != 0 }.minBy { kotlin.math.abs(idleLevels[it] - minutes) }
    var position by remember(minutes) { mutableStateOf(index.toFloat()) }
    val shown = idleLevels[position.roundToInt().coerceIn(idleLevels.indices)]
    // 和「按键效果」里的滑条同一写法：标题 + 主题色当前值一行，说明，滑条；两端标出最短和“永不”。
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
