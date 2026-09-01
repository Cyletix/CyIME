package com.kingzcheung.xime.service

import com.kingzcheung.xime.plugin.core.lua.CandidateTransformCandidate
import com.kingzcheung.xime.plugin.core.lua.CandidateTransformItem
import com.kingzcheung.xime.plugin.core.lua.CandidateTransformOutcome
import com.kingzcheung.xime.plugin.core.lua.CandidateTransformRequest
import com.kingzcheung.xime.plugin.core.lua.LuaScriptRuntime
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import com.kingzcheung.xime.util.FileLogger

/** 变换结果：显示候选（引擎引用 + 插件候选混合）+ 平行的上屏动作映射。 */
internal data class CandidateTransformResult(
    val candidates: List<RimeCandidate>,
    val actions: List<CandidateAction>,
)

/**
 * 候选词变换协调器（插件 candidate_transform 能力，首个 hotPath 能力）。
 *
 * 在 rime 返回候选之后、候选栏渲染之前同步调用插件 transformCandidates 修改候选；
 * 插件候选（text）点击后直接上屏插件文本，引擎引用（engine_index）走原引擎选词。
 *
 * 设计契约（docs/plugin-capability-registry.md §4.1/§5）：
 * - 线程：仅在 key-processing 线程调用（阻塞至多 15ms），主线程永不等待插件
 * - 短路：敏感输入框 / T9 / ascii 模式 / 空编码 / 无声明插件 → 不产生请求
 * - 熔断：连续 3 次失败（超时/报错/格式错）本会话禁用，插件重载或进程重启恢复
 * - 降级：任何失败回退原始候选，actions 为空 = 纯引擎语义
 */
internal class CandidateTransformCoordinator(private val service: XimeInputMethodService) {

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 3

        /**
         * 插件响应 → 显示候选 + 上屏动作（纯函数，便于单测）。
         * 语义校验：engine_index 越界/重复丢弃；text 空丢弃；总数上限截断。
         * 引用项 comment 覆盖显示注释；无可显示内容返回 null（不干预）。
         */
        internal fun buildDisplay(
            items: List<CandidateTransformItem>,
            engineCandidates: List<RimeCandidate>,
            maxCandidates: Int = LuaScriptRuntime.TRANSFORM_MAX_CANDIDATES,
        ): CandidateTransformResult? {
            val display = ArrayList<RimeCandidate>(items.size)
            val actions = ArrayList<CandidateAction>(items.size)
            val seenEngineIdx = HashSet<Int>()
            for (item in items) {
                if (display.size >= maxCandidates) break
                val engineIdx = item.engineIndex
                if (engineIdx != null) {
                    if (engineIdx < 0 || engineIdx >= engineCandidates.size || !seenEngineIdx.add(engineIdx)) continue
                    val engine = engineCandidates[engineIdx]
                    display.add(RimeCandidate(engine.text, item.comment ?: engine.comment))
                    actions.add(CandidateAction.engine(engineIdx))
                } else {
                    val text = item.text?.takeIf { it.isNotEmpty() } ?: continue
                    display.add(RimeCandidate(text, item.comment ?: ""))
                    actions.add(CandidateAction.plugin(text))
                }
            }
            if (display.isEmpty()) return null
            // 纯引擎引用（重排/去注释覆盖）也保留 actions：显示 index 与引擎 index 可能不同
            return CandidateTransformResult(display, actions)
        }
    }

    /** 连续失败熔断标记（本会话禁用；插件重载/进程重启恢复）。 */
    @Volatile
    private var disabledThisSession = false
    private var consecutiveFailures = 0

    /** 对一次引擎按键结果做变换。返回 null = 不干预，调用方原样渲染。 */
    fun transformFor(result: RimeProcessResult): CandidateTransformResult? {
        if (result.isAsciiMode) return null
        if (result.inputText.isEmpty() && result.candidates.isEmpty()) return null
        return transform(
            inputText = result.inputText,
            preedit = result.preeditText,
            engineCandidates = result.candidates.toList(),
            asciiMode = result.isAsciiMode,
        )
    }

    /**
     * 变换入口（key-processing 线程，阻塞至多 15ms）。
     * T9 路径不接入变换（v1 边界），此处防御再判。
     */
    fun transform(
        inputText: String,
        preedit: String,
        engineCandidates: List<RimeCandidate>,
        asciiMode: Boolean,
    ): CandidateTransformResult? {
        if (disabledThisSession) return null
        if (asciiMode) return null
        if (isT9Schema(service.uiState.value.currentSchemaId)) return null
        if (service.pluginEvents.isCurrentEditorSensitive) return null
        val runtime = findRuntime() ?: return null
        val outcome = runtime.transformCandidates(
            CandidateTransformRequest(
                inputText = inputText,
                preedit = preedit,
                candidates = engineCandidates.map { CandidateTransformCandidate(it.text, it.comment) },
                asciiMode = asciiMode,
            )
        )
        return when (outcome) {
            is CandidateTransformOutcome.NoResponse -> {
                consecutiveFailures = 0
                null
            }
            is CandidateTransformOutcome.Failed -> {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    disabledThisSession = true
                    FileLogger.w(
                        XimeInputMethodService.TAG,
                        "candidate_transform 连续失败 $consecutiveFailures 次，本会话禁用该插件变换"
                    )
                }
                null
            }
            is CandidateTransformOutcome.Success -> {
                consecutiveFailures = 0
                buildDisplay(outcome.items, engineCandidates)
            }
        }
    }

    /** 第一个声明 candidate_transform 且已加载的插件运行时（v1 单插件；链式变换为后续增强）。 */
    private fun findRuntime(): LuaScriptRuntime? {
        for ((_, loaded) in PluginManager.loadedPluginsFlow.value) {
            if (loaded.pluginInfo.capabilities?.candidateTransform != true) continue
            return loaded.script ?: continue
        }
        return null
    }
}
