package com.kingzcheung.xime.service

import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.rime.japanesePreedit
import com.kingzcheung.xime.rime.deleteJapaneseRomaji
import com.kingzcheung.xime.rime.insertJapaneseRomaji
import com.kingzcheung.xime.rime.boundariesAfterInsertion
import com.kingzcheung.xime.settings.JapaneseSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 所有方法在按键队列串行调用；会话或原编码改变就作废旧转换预览。 */
internal class JapaneseInputController(private val service: XimeInputMethodService) {
    /**
     * 转换预览：非空表示 preedit 已是假名/汉字预览。
     * UI 出口可能在主线程经 [normalizePreedit] 读取，故用 volatile。
     */
    @Volatile private var conversion: JapaneseConversion? = null
    private var session = -1L
    private var schema = ""
    private val engine get() = service.rimeEngine
    /** 按键路径的判定：引擎是权威源（切方案/部署期间可能领先或落后 uiState 一帧）。 */
    private fun available(): Boolean = !engine.isAsciiMode() &&
        com.kingzcheung.xime.settings.InputProfiles.describe(engine.getCurrentSchema()).capabilities.japaneseConversion

    /** 显示出口的判定：只读 uiState，不碰引擎——UI 每次刷新都会调用，不付出 JNI / 锁开销。 */
    private fun displayAvailable(): Boolean {
        val state = service.uiState.value
        return state.inputProfile.capabilities.japaneseConversion
    }
    fun displayCandidates(input: String) = conversion?.takeIf { it.input == input && session == service.uiState.value.inputSessionId }?.candidates?.toList()

    /**
     * 显示层归一化：末尾尚未拼完的罗马音显示为按下的字母（引擎回显会给 っ / ん）。
     *
     * 纯字符串运算、不改引擎状态、不持锁，因此由两个 UI 出口统一调用
     * （updateUIWithResult / applyComposition），按键后的刷新路径同样生效。
     * 转换预览期间 preedit 是假名/汉字预览，跳过归一化；非日语方案原样返回。
     */
    fun normalizePreedit(input: String, preedit: String): String =
        if (conversion == null && preedit.isNotEmpty() && displayAvailable()) japanesePreedit(input, preedit) else preedit

    private fun current(): JapaneseConversion? {
        if (!available() || session != service.uiState.value.inputSessionId || schema != engine.getCurrentSchema() || conversion?.input != engine.getInput()) conversion = null
        return conversion
    }
    private fun start(converted: Boolean = true, caretKana: Int? = null, caretAt: Int? = null,
        caretReading: Int? = null, pending: String = "", boundaries: List<Int>? = null): JapaneseConversion? {
        current()?.let { return it }
        val input = engine.getInput()
        if (input.isEmpty()) return null
        session = service.uiState.value.inputSessionId
        schema = engine.getCurrentSchema()
        return JapaneseConversion(engine, input, converted, caretKana, caretAt, caretReading, pending, boundaries)
            .also { conversion = it; it.refresh() }
    }

    /**
     * 光标在读音中间时光标处还没拼成假名的字母（[JapaneseConversion.pending]）：别的键来之前按原样写进编码，
     * 不能丢。返回写完后的转换状态（光标还在读音中间）或 null。
     */
    private fun flushPending(): JapaneseConversion? {
        val active = current() ?: return null
        if (active.pending.isEmpty()) return active
        val at = active.caretOffset
        engine.setInput(active.input.take(at) + active.pending + active.input.drop(at))
        conversion = null
        return start(converted = false, caretAt = at + active.pending.length)?.also { if (!it.partial) conversion = null }
    }

    /**
     * 光标在读音中间打字母（句中插字）：插在光标处，光标跟在新字后面，候选照旧只给光标前那段。
     * 以前一按字母就收起光标、字母接到读音末尾（11-05 用户反馈：句中光标处输入的内容出现在句末）。
     */
    private suspend fun insertAtCaret(active: JapaneseConversion, letter: String) {
        val at = active.caretOffset
        val inserted = insertJapaneseRomaji(active.input.take(at), active.pending + letter, active.input.drop(at))
        engine.setInput(inserted.input)
        conversion = null
        start(converted = false, caretAt = inserted.caret, pending = inserted.pending,
            boundaries = boundariesAfterInsertion(active.input, active.boundaries, at, inserted))
        show()
    }

    /**
     * 长按空格、底部留白滑动移光标（[steps] 格，负数向左）：打字中按假名移读音里的光标，和翻页键盘左右键相同。
     * 以前日语读音不接这条路，拖了光标没反应（11-05 用户反馈）。没在打字返回 false，照旧移输入框光标。
     */
    suspend fun moveCaret(steps: Int): Boolean {
        if (steps == 0 || !available() || engine.getInput().isEmpty()) return false
        val key = if (steps < 0) "japanese_left" else "japanese_right"
        repeat(kotlin.math.abs(steps).coerceAtMost(engine.getInput().length + 1)) { handleKey(key) }
        return true
    }

    /**
     * 在输入框里点了读音中间（[offset]：光标前有几个读音字）：光标挪到那里，接着打的字插在那里。
     * 已经转成汉字时不动。返回 false 表示没接手。
     */
    suspend fun placeCaret(offset: Int): Boolean {
        if (!available() || engine.getInput().isEmpty()) return false
        val old = flushPending() ?: current()
        if (old?.converted == true) return false
        conversion = null
        val active = start(converted = false, caretReading = offset)
        if (active != null && !active.partial) conversion = null
        show()
        return true
    }
    private suspend fun show() {
        val active = current()
        val result = engine.getProcessResult(true).let {
            // 转换预览显示假名本身；无转换时由 updateUIWithResult 出口做罗马音归一化（显示按下的字母）
            if (active == null) it
            else it.copy(preeditText = active.preview, candidates = active.candidates, hasNextPage = false, hasPrevPage = false)
        }
        val owner = service.uiState.value.inputSessionId
        withContext(Dispatchers.Main) {
            InputCommandOwner.requireOwner().requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
            if (owner == service.uiState.value.inputSessionId) service.sessionController.updateUIWithResult(result)
        }
    }
    suspend fun cancel() { conversion = null; show() }
    /** 只挪了光标、没转换时没有转换预览（预览里有光标竖线，不能上屏）。 */
    fun conversionPreview(): String? = current()?.takeIf { it.converted }?.preview
    suspend fun commit(index: Int? = null): Boolean {
        flushPending()
        val active = current() ?: return false
        // 点候选：只上屏这一段，后面的读音留着接着打。
        if (index != null) return confirmSegment(index)
        // 只挪了光标：回到平常的打字状态，由回车等按键按原来的规则结束。
        if (!active.converted) { conversion = null; return false }
        val text = active.preview
        val owner = session
        conversion = null
        if (owner == service.uiState.value.inputSessionId) service.keyRouter.commitSelectedText(text)
        return true
    }

    /**
     * 确认当前段（空格、点候选）：上屏光标前或转换段转出的字，后面的读音留在编码里接着打、接着转换
     * （Gboard、iOS 日文键盘同样）。当前段已到末尾时与整段上屏相同。
     */
    private suspend fun confirmSegment(index: Int? = null): Boolean {
        val active = current() ?: return false
        // 光标在读音最前面：光标前没有字，空格按平常整段处理。
        if (active.atStart && index == null) { conversion = null; return false }
        if (index != null) active.choose(index)
        val text = active.segment
        val rest = active.rest
        val owner = session
        conversion = null
        if (owner != service.uiState.value.inputSessionId) return true
        if (rest.isEmpty()) { service.keyRouter.commitSelectedText(text); return true }
        if (service.keyRouter.commitPreparedText(text, t9 = false).accepted && owner == service.uiState.value.inputSessionId) {
            engine.setInput(rest)
            show()
        }
        return true
    }
    suspend fun prepareKana(modify: Boolean) { if (current() != null) { if (modify) cancel() else commit() } }
    suspend fun handleKey(key: String, shifted: Boolean = false): Boolean {
        if (!available()) { conversion = null; return false }
        // 光标在读音中间打字母、长音：插在光标处。
        current()?.takeIf { !it.converted && it.partial && isRomajiKey(key) }?.let {
            insertAtCaret(it, if (shifted) key.uppercase() else key)
            return true
        }
        if (key != "delete") flushPending()
        when (key) {
            // 已转换：再按换下一个候选；只挪过光标：把光标前那段转成首选。
            "japanese_convert" -> {
                val old = current(); val active = start()
                if (old != null) { if (old.converted) active?.cycle() else active?.convert() }
                show(); return true
            }
            "japanese_undo" -> { cancel(); return true }
            // 日文翻页键盘的左右键：没在打字时移动正文光标；打字中按假名移动读音里的光标（候选只给光标前那段）；
            // 按过変換后调整转换段长短。光标回到末尾时回到平常的打字状态。
            "japanese_left", "japanese_right" -> {
                val steps = if (key.endsWith("left")) -1 else 1
                val old = current()
                if (old == null && engine.getInput().isEmpty()) {
                    service.schemaController.moveJapaneseCursorFromKeyQueue(steps)
                    return true
                }
                // 光标已在读音末尾，右键无处可去。
                if (old == null && steps > 0) return true
                val active = old ?: start(converted = false) ?: return true
                val moved = active.moveRange(steps)
                if (!active.converted && !active.partial) conversion = null
                if (moved || old == null) show()
                return true
            }
            "delete", "clear_composition", "clear_all" -> conversion = null
            "space" -> if (confirmSegment()) return true
            "enter" -> if (commit()) return true
            // 只挪过光标时不上屏，先收起光标再交给原来的按键处理。
            else -> current()?.let { if (it.converted) commit() else cancel() }
        }
        return false
    }
    suspend fun deleteKana(): Boolean {
        if (!available()) return false
        val input = engine.getInput()
        if (input.isEmpty()) { conversion = null; return false }
        val active = current()
        conversion = null
        if (active != null && !active.converted && active.partial && active.pending.isNotEmpty()) {
            // 光标处还没拼成假名的字母：先删它。
            val (at, rest) = active.caretOffset to active.pending.dropLast(1)
            start(converted = false, caretAt = at, pending = rest, boundaries = active.boundaries)
            show()
            return true
        }
        // 光标在读音最前面：前面没有可删的，光标不动。
        if (active != null && !active.converted && active.partial && active.atStart) { conversion = active; return true }
        if (active != null && !active.converted && active.partial) {
            // 光标在读音中间：删光标前的那个假名，光标留在原处（删到开头就停在读音最前面）。
            val (next, caretKana) = active.withoutKanaBeforeCaret()
            engine.setInput(next)
            if (caretKana > 0) start(converted = false, caretKana = caretKana) else start(converted = false, caretAt = 0)
            if (conversion?.partial == false) conversion = null
            show()
            return true
        }
        // 删除单位与显示同源（romajiDeleteStart）：未拼完的罗马音只删一个字母，
        // 已拼完的假名整体删。不能再用引擎读音长度探测边界——ん 与 っ 等长时
        // 判不出假名边界，会把整串一次删光（2026-09-25 复现）。
        engine.setInput(deleteJapaneseRomaji(input))
        show()
        return true
    }

    /** 读音里的一个键：字母或长音 -（L 当长音时也是字母）。 */
    private fun isRomajiKey(key: String): Boolean =
        key.length == 1 && (key[0] in 'a'..'z' || key[0] in 'A'..'Z' || key[0] == '-')
}
