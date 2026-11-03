package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.PerformanceCosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本机该用的那组实测数（见 [PerformanceCosts.source]），在后台读；读完前和没有数据时为 null。 */
@Composable
internal fun rememberPerformanceCostSource(): PerformanceCosts.Source? {
    val context = LocalContext.current
    val source by produceState<PerformanceCosts.Source?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { PerformanceCosts.source(context) }.getOrNull() }
    }
    return source
}

/**
 * 开关说明下面那一行“性能代价：每键 +1.2 ms（慢 4%）· 内存 +30 MB”；还没实测的写“性能代价（估算）：每键约 …”。
 * 和「已被…锁定」那行同一位置、同一字号，用次要色，不当成警告。
 */
@Composable
internal fun PerformanceCostNote(item: PerformanceCosts.Item, modifier: Modifier = Modifier, label: String = "性能开销") {
    val source = rememberPerformanceCostSource() ?: return
    val text = PerformanceCosts.note(source, item.id, label) ?: return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp).testTag("perf-cost:${item.id}"))
}

/** 几项合起来的代价一行（同 [PerformanceCostNote] 的样式），如「九键按数字也模糊」下面已开各组的合计；一组都没开不显示。 */
@Composable
internal fun PerformanceCostTotalNote(items: Set<PerformanceCosts.Item>, label: String, tag: String, modifier: Modifier = Modifier) {
    val source = rememberPerformanceCostSource() ?: return
    val text = PerformanceCosts.totalNote(source, items.mapTo(linkedSetOf()) { it.id }, label) ?: return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp).testTag("perf-cost-total:$tag"))
}

/**
 * 「性能与省电」里的“性能代价”：当前开着的耗性能开关合起来每键慢多少、多占多少内存，下面列出每一项。
 * [on] 由页面传入，方便页面刷新后重算。
 */
@Composable
internal fun PerformanceCostTotalSection(on: Set<String>) {
    val source = rememberPerformanceCostSource()
    val context = LocalContext.current
    // 已应用的导入词库一共多少词（词库页「应用更改」之后才算）。
    val dictionaryWords by produceState(0) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val state = com.kingzcheung.xime.settings.CellDictionaryManager.store(context).read()
                state.installed.filter { it.id in state.applied }.sumOf { it.count }
            }.getOrDefault(0)
        }
    }
    SettingsSection(title = "性能开销") {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("perf-cost-total"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (source == null) {
                Text("读不到开销表(安装包里的 perf_costs.json)，暂时算不出总开销。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val device = source.device
            val switches = PerformanceCosts.total(device, on)
            // 导入词库按实测曲线另加在开关合计上（词库和开关是同一次基线测的）。
            val dictionaries = PerformanceCosts.dictionaryCost(device, dictionaryWords)
                ?.copy(baselineKeyMs = device.dictionaryBaseline?.keyMs)
            val total = if (dictionaries == null) switches else switches.copy(
                keyMs = switches.keyMs + (dictionaries.keyMs ?: 0.0),
                memoryMb = switches.memoryMb + (dictionaries.memoryMb ?: 0.0),
                measured = switches.measured)
            val listed = PerformanceCosts.Item.entries.filter { it.id in on }
            Row(Modifier.fillMaxWidth()) {
                Text("当前总开销", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(if (listed.isEmpty() && dictionaries == null) "全关，没有额外开销"
                    else PerformanceCosts.describe(PerformanceCosts.Cost(total.keyMs, total.memoryMb, total.baselineKeyMs,
                        estimated = total.estimated.isNotEmpty()), device) ?: "测不出差别",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            val how = when {
                listed.isEmpty() -> null
                dictionaries != null && total.measured -> "开关这组一起实测过；导入的词库按实测曲线另加。"
                dictionaries != null -> "开关按单项实测相加，再用最接近的一组实测组合校准；导入的词库按实测曲线另加。"
                total.measured -> "这组开关一起实测过，直接用实测值。"
                total.estimated.size == listed.size -> "按单项相加。"
                else -> "几项会抢同一个线程，不能直接相加：按单项实测相加，再用最接近的一组实测组合校准。"
            }
            how?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(buildString {
                append(when {
                    source.local -> "本机实测"
                    device.allEstimated -> "按 ${PerformanceCosts.deviceName(device)} 的基线${if (source.scaled) "，按本机档位折算" else ""}"
                    source.sameModel -> "同型号实测"
                    source.scaled -> "${PerformanceCosts.deviceName(device)} 实测按本机档位折算，仅供参考"
                    else -> "${PerformanceCosts.deviceName(device)} 实测，仅供参考"
                })
                if (device.measuredAt.isNotBlank()) append("(${device.measuredAt}")
                if (device.layout.isNotBlank()) append(if (device.measuredAt.isNotBlank()) "，${device.layout}" else "(${device.layout}")
                if (device.measuredAt.isNotBlank() || device.layout.isNotBlank()) append(")")
                append("。全关时每键 ${PerformanceCosts.oneDecimal(device.baseline.keyMs)} ms")
                device.baseline.memoryMb?.let { append("，内存 ${Math.round(it)} MB") }
                append("。")
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (listed.isNotEmpty() || dictionaries != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                // 按每键耗时从大到小，最耗的排前面；没测过的放最后写明。
                val measured = total.items.mapNotNull { (id, cost) -> PerformanceCosts.Item.byId(id)?.let { it to cost } }
                // 没有值得说的开销的项不列（开关下面也不写）。
                measured.forEach { (item, cost) ->
                    val text = PerformanceCosts.describe(cost, device) ?: return@forEach
                    Row(Modifier.fillMaxWidth()) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(text, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                dictionaries?.let { cost ->
                    Row(Modifier.fillMaxWidth().testTag("perf-cost-dictionaries")) {
                        Text("导入的词库($dictionaryWords 词)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(PerformanceCosts.describe(cost, device) ?: "测不出差别", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val missing = listed.filter { it.id in total.unmeasured }
                if (missing.isNotEmpty()) Text("开着但表里没有：" + missing.joinToString("、") { it.title },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
