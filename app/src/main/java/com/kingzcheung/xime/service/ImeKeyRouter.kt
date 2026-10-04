package com.kingzcheung.xime.service

import com.kingzcheung.xime.clipboard.ClipboardDraftSaveResult
import com.kingzcheung.xime.clipboard.submitClipboardDraft

import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.rime.resolveRimeCandidateIndex
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 按键输入路由。
 *
 * 承载按键派发（handleKeyPress）、长按退格合并、候选选择/翻页、计算器候选等逻辑。
 * 所有共享状态通过 service 引用访问（同模块 internal 成员）。
 */
internal class ImeKeyRouter(private val service: XimeInputMethodService) {
    @Volatile internal var letterNeighbors: Pair<String, String> = "" to ""
    private val hardwareNavigation = HardwareCandidateNavigation()
    private val hardwareSelectionRevision = androidx.compose.runtime.mutableIntStateOf(0)

    internal fun handleHardwareCandidateKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && hasPendingCandidateCommit) {
            cancelRejectedCommit()
            return true
        }
        // Do not claim an event that the input FIFO cannot admit. Android must still
        // be able to move the editor cursor while the engine is starting/redeploying.
        if (service.inputReadiness.ticket() == null) return false
        val key = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> "hardware_candidate_left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "hardware_candidate_right"
            KeyEvent.KEYCODE_DPAD_UP -> "hardware_candidate_up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "hardware_candidate_down"
            KeyEvent.KEYCODE_ESCAPE -> "hardware_candidate_cancel"
            KeyEvent.KEYCODE_SPACE -> "space"
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "enter"
            else -> if (hardwareCandidateDigitIndex(keyCode, event.isShiftPressed) != null)
                keyCodeToKey(keyCode, false) ?: return false else return false
        }
        handleKeyPress(key, event.isShiftPressed, hardwareEvent = KeyEvent(event))
        return true
    }

    internal fun releaseHardwareCandidateKey(keyCode: Int) {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> HardwareCandidateKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> HardwareCandidateKey.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> HardwareCandidateKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> HardwareCandidateKey.DOWN
            else -> return
        }
        postRimeJob { hardwareNavigation.releasePress(key) }
    }

    /** Candidate-window arrow buttons use the same ordered selection as physical arrows. */
    internal fun moveHardwareCandidate(direction: Int) {
        val code = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        postRimeJob {
            if (!hasPendingCandidateCommit) {
                processHardwareCandidateKey(KeyEvent(KeyEvent.ACTION_DOWN, code), pageAtBoundary = true)
            }
            hardwareNavigation.releasePress(if (direction < 0) HardwareCandidateKey.LEFT else HardwareCandidateKey.RIGHT)
        }
    }

    internal fun hardwareCandidateHighlight(
        state: CandidateState,
        english: Boolean,
        predictionPending: Boolean = false,
    ): Int {
        return hardwareCandidateSelection(state, english, predictionPending) ?: 0
    }

    internal fun hardwareCandidateSelection(
        state: CandidateState, english: Boolean, predictionPending: Boolean = false,
    ): Int? {
        val snapshot = hardwareCandidateSnapshot(state, english, predictionPending)
        return hardwareSelectionRevision.intValue.let { hardwareNavigation.selectedIndex(snapshot) }
    }

    internal fun resetHardwareCandidateSelection() {
        postRimeJob {
            hardwareNavigation.clear()
            withEditor { publishHardwareCandidateSelection() }
        }
    }

    private fun publishHardwareCandidateSelection() {
        hardwareSelectionRevision.intValue++
    }

    private fun hardwareCandidateSnapshot(
        state: CandidateState,
        english: Boolean,
        predictionPending: Boolean,
    ) = HardwareCandidateSnapshot(service.uiState.value.inputSessionId, service.uiState.value.inputProfile,
        state, english, predictionPending, service.predictionManager.lastCommittedText,
        associationLimit = service.hardwareAssociationLimit(),
        expanded = !service.uiState.value.isCompact && service.keyboardViewModel.candidatePageExpanded.value,
        singleCharOnly = service.keyboardViewModel.singleCharFilter.value)

    @Volatile private var pendingCandidateCommit: PendingCandidateCommit? = null
    internal val hasPendingCandidateCommit: Boolean get() = pendingCandidateCommit != null

    internal fun discardPendingCandidateCommit() {
        pendingCandidateCommit = null
        service.uiState.value = service.uiState.value.copy(rejectedCommitText = null)
    }

    internal fun retryRejectedCommit() {
        val pending = pendingCandidateCommit ?: return
        if (!service.inputReadiness.accepts(pending.owner.admission) ||
            service.uiState.value.inputSessionId != pending.owner.editor) {
            discardPendingCandidateCommit()
            return
        }
        pending.owner.runInline {
            postRimeJob {
                if (pendingCandidateCommit === pending) deliverCandidateCommit(pending)
            }
        }
    }

    internal fun cancelRejectedCommit() {
        val pending = pendingCandidateCommit ?: return
        if (!service.inputReadiness.accepts(pending.owner.admission) ||
            service.uiState.value.inputSessionId != pending.owner.editor) {
            discardPendingCandidateCommit()
            return
        }
        pending.owner.runInline {
            postRimeJob {
                if (pendingCandidateCommit !== pending) return@postRimeJob
                clearInputStateForKeys()
                withEditor { discardPendingCandidateCommit() }
            }
        }
    }

    private fun commandOwner(admission: Long): InputCommandOwner {
        val editor = service.uiState.value.inputSessionId
        return InputCommandOwner(admission, editor) {
            if (!service.inputReadiness.accepts(admission) || service.uiState.value.inputSessionId != editor) {
                throw kotlinx.coroutines.CancellationException("Expired input command")
            }
        }
    }

    /** Called at the Main user-operation boundary, before work enters the input queue. */
    internal fun captureInputContext(): kotlin.coroutines.CoroutineContext {
        val inherited = InputCommandOwner.current.get()
        if (inherited != null) return inherited.context()
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        val admission = service.inputReadiness.ticket()
            ?: throw kotlinx.coroutines.CancellationException("Input is not ready")
        return commandOwner(admission).context()
    }

    private suspend fun <T> withEditor(block: suspend CoroutineScope.() -> T): T =
        withContext(Dispatchers.Main) {
            InputCommandOwner.requireOwner().requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
            block()
        }

    private fun publishUi(block: suspend () -> Unit) {
        val owner = InputCommandOwner.requireOwner()
        service.uiEventChannel.trySend {
            if (service.inputReadiness.accepts(owner.admission) && service.uiState.value.inputSessionId == owner.editor) {
                withContext(owner.context()) { block() }
            }
        }
    }

    /**
     * 候选词变换（hotPath 插件能力）+ 发送 UI 更新。
     * 必须在 key-processing 线程调用：同步等插件至多 15ms；
     * 超时/失败/插件不干预（null）均回退原始候选（actions 为空 = 纯引擎语义）。
     */
    private fun sendTransformedResult(
        result: com.kingzcheung.xime.rime.RimeProcessResult,
        afterUpdate: (suspend () -> Unit)? = null,
    ) {
        // 日语罗马音的显示归一化收口在 updateUIWithResult（刷新路径同样生效），此处不再单独加工
        val transformed = service.candidateTransform.transformFor(result)
        publishUi {
            if (!service.rimeEngine.isCandidateRevisionCurrent(result.engineRevision)) return@publishUi
            service.sessionController.updateUIWithResult(
                transformed?.let { result.copy(candidates = it.candidates.toTypedArray()) } ?: result,
                transformed?.actions ?: emptyList()
            )
            if (afterUpdate != null) afterUpdate()
        }
    }

    internal fun handleKeyPress(key: String, isShifted: Boolean, hardwarePageDirection: Int = 0,
        languageSwitchMode: com.kingzcheung.xime.settings.LanguageSwitchMode? = null,
        hardwareEvent: KeyEvent? = null) {
        if (hasPendingCandidateCommit) return
        val inherited = InputCommandOwner.current.get()
        if (inherited != null) {
            inherited.requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
            routeKeyPress(key, isShifted, hardwarePageDirection, languageSwitchMode, hardwareEvent)
            return
        }
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "Only a direct Main input entry may create command ownership"
        }
        val admission = service.inputReadiness.ticket() ?: return
        commandOwner(admission).runInline { routeKeyPress(key, isShifted, hardwarePageDirection, languageSwitchMode, hardwareEvent) }
    }

    private fun routeKeyPress(key: String, isShifted: Boolean, hardwarePageDirection: Int,
        languageSwitchMode: com.kingzcheung.xime.settings.LanguageSwitchMode?, hardwareEvent: KeyEvent?) {
        // 空键无任何按键语义，且下游 Rime 路由按 key[0] 取码（key.lowercase()[0]），
        // 空串会越界崩溃（2026-09-14 真机实证：滑动手势 commit 值为空时触发）。
        if (key.isEmpty()) return
        val admission = service.inputReadiness.ticket() ?: return
        // Capture what Space meant at touch time; a later Next tap must not change this queued commit.
        val spaceSnapshot = service.candidateState.value.takeIf {
            hardwareEvent == null && key == "space" && it.candidates.isNotEmpty() &&
                it.usesT9CandidateNavigation(service.uiState.value.inputProfile)
        }
        // A language key does not edit text. Keep an outstanding voice final while the
        // language observer decides whether this is the same native/English pair.
        if (key != "ime_switch") service.voiceRecognitionHandler.abandonPendingOnManualInput()
        if (service.uiState.value.toolPanelInputFocused) {
            val candState = service.candidateState.value
            val hasComposing = candState.isComposing || candState.inputText.isNotEmpty()
            when (key) {
                "enter" -> {
                    if (hasComposing) {
                        // 组合态：先把拼音提交进面板输入框，再触发生成
                        val input = candState.inputText
                        service.mainHandler.post {
                            if (!service.inputReadiness.accepts(admission)) return@post
                            ToolPanelEditTextHolder.editText?.let { et ->
                                val start = et.selectionStart.coerceAtLeast(0)
                                et.text?.replace(start, et.selectionEnd.coerceAtLeast(start), input)
                                try { et.setSelection(start + input.length) } catch (_: Exception) {}
                            }
                            service.rimeEngine.clearComposition()
                            service.candidateState.value = service.candidateState.value.copy(
                                inputText = "",
                                preeditText = "",
                                pendingEnglishText = "",
                                candidates = emptyList(),
                                candidateComments = emptyList(),
                                associationCandidates = emptyList(),
                                isComposing = false,
                                candidateActions = emptyList()
                            )
                        }
                    }
                    service.triggerToolPanelGenerate()
                    return
                }
                "delete" -> {
                    if (hasComposing) {
                        // 组合态：退格走 Rime，更新候选栏
                        service.rimeEngine.processKey(0xff08, 0)
                        val result = service.rimeEngine.getProcessResult(true)
                        if (result.inputText.isEmpty()) {
                            service.rimeEngine.clearComposition()
                        }
                        sendTransformedResult(result)
                    } else {
                        ToolPanelEditTextHolder.editText?.let { et ->
                            val start = et.selectionStart.coerceAtLeast(0)
                            val end = et.selectionEnd.coerceAtLeast(start)
                            if (start == end && start > 0) {
                                et.text?.delete(start - 1, start)
                                try { et.setSelection(start - 1) } catch (_: Exception) {}
                            } else if (end > start) {
                                et.text?.delete(start, end)
                                try { et.setSelection(start) } catch (_: Exception) {}
                            }
                        }
                    }
                    return
                }
                "space" -> {
                    if (hardwareEvent == null) {
                        if (hasComposing && candState.candidates.isNotEmpty()) {
                            val snapshot = spaceSnapshot ?: candState
                            val index = snapshot.spaceCandidateIndex(service.uiState.value.inputProfile)
                            postRimeJob { selectCandidateAsync(index, snapshot = snapshot) }
                        } else {
                            ToolPanelEditTextHolder.editText?.let { et ->
                                val start = et.selectionStart.coerceAtLeast(0)
                                et.text?.insert(start, service.textCommit.keyboardLiteral(" "))
                                try { et.setSelection(start + 1) } catch (_: Exception) {}
                            }
                        }
                        return
                    }
                }
                else -> {
                    if (key.length == 1) {
                        val isLetter = key.matches(Regex("[a-zA-Z]"))
                        val isChineseMode = !service.uiState.value.isAsciiMode
                        if (isLetter && isChineseMode) {
                            // 中文模式：字母进 Rime 拼音组合，候选栏选词后经 commitText 重定向进面板输入框
                            val keyCode = key.lowercase()[0].code
                            val result = service.rimeEngine.processKeyAndGetResult(keyCode, 0)
                            if (result.processed) {
                                sendTransformedResult(result)
                            }
                            return
                        }
                        val char = if (isShifted) key.uppercase() else key
                        ToolPanelEditTextHolder.editText?.let { et ->
                            val start = et.selectionStart.coerceAtLeast(0)
                            et.text?.insert(start, char)
                            try { et.setSelection(start + char.length) } catch (_: Exception) {}
                        }
                        return
                    }
                }
            }
        }
        // 表单显示期间（无论 EditText 是否持有焦点）都拦截：
        // 焦点可能因点击表单外区域丢失，此时回车仍应提交并关闭表单、
        // 退格仍应作用于表单输入框，否则按键落入普通输入语义造成"关闭无效"。
        if (service.uiState.value.showQuickSendForm) {
            when (key) {
                "enter" -> {
                    val editText = QuickSendFormEditTextHolder.editText
                    val codeEditText = QuickSendFormCodeEditTextHolder.editText
                    val text = editText?.text?.toString() ?: ""
                    val code = codeEditText?.text?.toString()?.trim() ?: ""
                    val s = service.uiState.value
                    val result = submitClipboardDraft(
                        text, code, s.quickSendEditingItemId,
                        add = service.keyboardViewModel::addPinnedText,
                        update = service.keyboardViewModel::updateClipboardItem,
                    )
                    if (result != ClipboardDraftSaveResult.ACCEPTED) {
                        if (editText != null) {
                            editText.error = result.errorMessage
                            editText.requestFocus()
                        } else {
                            android.widget.Toast.makeText(service, result.errorMessage, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    service.uiState.value = s.copy(
                        showQuickSendForm = false,
                        quickSendFormFocused = false,
                        quickSendCodeFocused = false,
                        quickSendEditingItemId = null,
                        quickSendEditingItemText = "",
                        quickSendEditingItemCode = "",
                        enterKeyText = "发送",
                    )
                    QuickSendFormEditTextHolder.editText = null
                    QuickSendFormCodeEditTextHolder.editText = null
                    service.keyboardViewModel.showOverlay(OverlayRoute.Clipboard(0))
                    return
                }
                "delete" -> {
                    val candState = service.candidateState.value
                    val isComposing = candState.isComposing || candState.inputText.isNotEmpty()
                    if (isComposing) {
                        // Rime 有组合态 → 转发退格到 Rime 清空候选字母/联想词
                        service.rimeEngine.processKey(0xff08, 0)
                        val result = service.rimeEngine.getProcessResult(true)
                        if (result.inputText.isEmpty()) {
                            service.rimeEngine.clearComposition()
                        }
                        sendTransformedResult(result)
                    } else {
                        // 无组合态 → 按焦点路由删除表单内（文本框/触发编码框）已上屏文字
                        service.deleteInQuickSendForm()
                    }
                    return
                }
            }
        }
        // 长按只允许一个待处理重复；每次独立点击仍按原始顺序执行。
        if (key == "delete") {
            handleDeleteKey()
            return
        }
        service.inputCommands.submit(InputCommandOwner.requireOwner().context()) command@{
            InputCommandOwner.requireOwner().requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
            if (!service.inputReadiness.accepts(admission) || hasPendingCandidateCommit) return@command
            if (hardwareEvent != null) {
                if (processHardwareCandidateKey(hardwareEvent)) return@command
            } else {
                hardwareNavigation.clear()
            }
            if (hardwareEvent == null && key.length == 1) {
                withEditor {
                    service.predictionManager.invalidatePendingPredictions()
                    service.candidateState.value = service.candidateState.value.copy(associationCandidates = emptyList())
                }
            }
            if (hardwarePageDirection != 0 &&
                (service.rimeEngine.compositionActiveForDeletion() != false || service.t9PartialSegments.isNotEmpty())) {
                // Consume even at either boundary or when decoding has no candidates yet.
                // Never turn a reserved paging key into punctuation in an unfinished composition.
                if (service.keyboardViewModel.candidatePageExpanded.value) {
                    withEditor { service.expandedPageScroll(hardwarePageDirection) }
                } else {
                    if (hardwarePageDirection > 0 && service.rimeEngine.hasNextPage()) service.rimeEngine.pageDown()
                    else if (hardwarePageDirection < 0 && service.rimeEngine.hasPrevPage()) service.rimeEngine.pageUp()
                    withEditor { service.updateUI() }
                }
                withEditor { publishHardwareCandidateSelection() }
                return@command
            }
            val geometry = letterNeighbors
            service.rimeEngine.setNeighborMap(if (geometry.first == service.uiState.value.currentSchemaId) geometry.second else "")
            if (service.japaneseInputController.handleKey(key)) return@command
            val state = service.uiState.value
            val candState = service.candidateState.value
            var needsUIUpdate = false
            var pendingResult: com.kingzcheung.xime.rime.RimeProcessResult? = null
            var committedText: String? = null
            
            when (key) {
                "clear_composition" -> {
                    // 只清输入态（预编辑/候选/联想/partial 累积/计算器/左栏），不动已上屏文本。
                    // 清理逻辑见 clearInputStateForKeys()（与 clear_all 输入态分支共用）。
                    clearInputStateForKeys()
                    needsUIUpdate = true
                }
                "clear_all" -> {
                    // 上滑清空 = 多次退格快捷方式（对标主流输入法）：输入态只清输入态，空闲态清空全部已上屏。
                    // 引擎繁忙时不把未知状态当成空闲，更不能清正文。
                    if (service.rimeEngine.compositionActiveForDeletion() == null) return@command
                    if (hasInputState(candState)) {
                        // 输入态：只清输入态（等价于 clear_composition），并记录 lastClearedText 供下滑撤回。
                        // 需在 clearInputStateForKeys() 之前记录（该函数会清空 preeditText/inputText）。
                        val pendingEnglish = candState.pendingEnglishText
                        if (pendingEnglish.isNotEmpty()) {
                            // 直接上屏模式：英文编码已逐字落盘，"清输入态"需回删屏上对应字符；
                            // 校验光标前文本一致才删除并记录撤回，否则只清状态不动已上屏文本。
                            val removed = withEditor {
                                val ic = service.currentInputConnection ?: return@withEditor false
                                val before = runCatching {
                                    ic.getTextBeforeCursor(pendingEnglish.length, 0)?.toString()
                                }.getOrNull()
                                before == pendingEnglish && ic.deleteSurroundingText(pendingEnglish.length, 0)
                            }
                            if (removed) service.lastClearedText = pendingEnglish
                        } else {
                            // 撤回重放用 inputText（原始键入串）：preeditText 现为带回显分隔符的
                            // 展示串（如 ni'hao），上屏必须无分隔符版本。T9 时 inputText 为合成显示态，
                            // 与 preeditText 同值，行为不变。
                            service.lastClearedText = candState.inputText
                        }
                        clearInputStateForKeys()
                    } else {
                        // 空闲态：清空输入框全部已上屏文本。
                        service.calculatorEngine.clear()
                        updateCalculatorCandidates()
                        // 记录撤回内容：输入框模式 getTextBeforeCursor 已含 composing 区（与 inputText 同源，
                        // 避免重复拼接）；候选栏模式输入框只有已上屏文本，需补候选栏编码 inputText。
                        // 英文输入态已被 hasInputState() 拦截，此处 pendingEnglishText 恒为空，拼接仅作防御。
                        val codeInInputBox = SettingsPreferences.getInputTextLocation(service) ==
                            SettingsPreferences.INPUT_TEXT_INPUT_BOX
                        val inputFieldText = withEditor {
                            service.currentInputConnection?.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0)?.toString() ?: ""
                        }
                        service.lastClearedText = when {
                            codeInInputBox -> inputFieldText + candState.pendingEnglishText
                            else -> inputFieldText + candState.inputText + candState.pendingEnglishText
                        }
                        // 清空 partial 累积，避免残留词被 buildT9DisplayState 拼进下一轮 preedit。
                        service.t9PartialSegments.clear()
                        service.rimeEngine.clearComposition()
                        service.candidateState.value = service.candidateState.value.copy(
                            candidates = emptyList(),
                            candidateComments = emptyList(),
                            associationCandidates = emptyList(),
                            pendingEnglishText = "",
                            inputText = "",
                            isComposing = false,
                            isShowingRecentClipboard = false,
                            candidateActions = emptyList()
                        )
                        withEditor {
                            service.currentInputConnection?.let {
                                service.endComposingInputBox()
                                // 删除输入框中所有文字
                                val textLen = inputFieldText.length
                                if (textLen > 0) {
                                    it.deleteSurroundingText(textLen, 0)
                                }
                            }
                        }
                    }
                    needsUIUpdate = true
                }
                "undo_clear" -> {
                    // 下滑撤回 = 撤销"上滑清空"，仅空闲态有效（输入态恢复会插入错误位置），
                    // 判定与 clear_all 共用 hasInputState()。
                    if (!hasInputState(candState)) {
                        val text = service.lastClearedText
                        if (text.isNotEmpty()) {
                            service.lastClearedText = ""
                            withEditor {
                                val ic = service.currentInputConnection
                                if (ic != null) {
                                    // newCursorPosition=1：光标停在撤回内容末尾；
                                    // 传 text.length 会被 clamp 到整段文本末尾。
                                    ic.commitText(text, 1)
                                }
                            }
                        }
                    }
                    needsUIUpdate = true
                }
                "enter" -> {
                    service.calculatorEngine.clear()
                    val japaneseResult = service.rimeEngine.processJapaneseEnterIfComposing()
                    if (japaneseResult != null) {
                        // 原生 Return 按日语方案提交假名读音；commit 不能经过可覆盖的 UI channel。
                        if (japaneseResult.committedText.isNotEmpty()) {
                            withEditor { service.commitText(japaneseResult.committedText) }
                        }
                        if (japaneseResult.processed || japaneseResult.committedText.isNotEmpty()) {
                            sendTransformedResult(japaneseResult)
                        }
                        // 未处理时保留输入；成功时按结果刷新，不能再 clearComposition 或重复取 commit。
                        return@command
                    }
                    val engineInput = service.rimeEngine.getInput()
                    // UI publication may lag the FIFO. Enter consumes unfinished input;
                    // only a subsequent, idle Enter may invoke the editor action.
                    if (engineInput.isNotEmpty() || candState.isComposing ||
                        (!state.isAsciiMode && candState.pendingEnglishText.isNotEmpty())) {
                        // T9 模式提交完整预编辑（含 partial commit 累积），非 T9 模式用 RIME input。
                        val isT9 = isT9Schema(state.currentSchemaId)
                        val input = rawCompositionCommitText(engineInput, candState.preeditText, isT9)
                        if (input.isNotEmpty()) {
                            val accepted = withEditor { service.submitText(input).accepted }
                            if (!accepted) return@command
                        }
                        if (isT9) {
                            // 同步清空，避免异步 postRimeJob 延迟导致后续 backspace 拿到旧状态。
                            service.t9PartialSegments.clear()
                            service.rimeEngine.setInput("")
                            service.rimeEngine.clearComposition()
                        } else {
                            service.rimeEngine.clearComposition()
                        }
                        withEditor { service.endComposingInputBox() }
                        needsUIUpdate = true
                    } else {
                        service.rimeEngine.clearComposition()
                        withEditor {
                            val imeOptions = service.currentInputEditorInfo?.imeOptions ?: 0
                            val action = imeOptions and EditorInfo.IME_MASK_ACTION
                            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                            when {
                                noEnterAction -> service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                                action == EditorInfo.IME_ACTION_GO ||
                                action == EditorInfo.IME_ACTION_SEARCH ||
                                action == EditorInfo.IME_ACTION_SEND ||
                                action == EditorInfo.IME_ACTION_NEXT ||
                                action == EditorInfo.IME_ACTION_DONE ->
                                    service.currentInputConnection?.performEditorAction(action)
                                else -> service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                            }
                        }
                    }
                    withEditor {
                        service.candidateState.value = service.candidateState.value.copy(
                            inputText = "",
                            preeditText = "",
                            pendingEnglishText = "",
                            candidates = emptyList(),
                            candidateComments = emptyList(),
                            associationCandidates = emptyList(),
                            isComposing = false
                        )
                        if (isT9Schema(state.currentSchemaId)) {
                            service.keyboardCallbacks?.onT9CompositionCleared?.invoke()
                            service.uiState.value = service.uiState.value.copy(
                                t9RightCandidateSelectedCount = 0,
                                t9SelectedCandidatePinyin = ""
                            )
                        }
                    }
                }
                "space" -> {
                    if (spaceSnapshot != null) {
                        selectCandidateAsync(spaceSnapshot.highlightedCandidateIndex, snapshot = spaceSnapshot)
                        return@command
                    }
                    val pendingEnglish = candState.pendingEnglishText

                    if (pendingEnglish.isNotEmpty()) {
                        // 直接上屏模式：词已逐字落盘，此处只提交空格并结束本轮英文输入。
                        withEditor {
                            service.commitText(" ")
                            service.candidateState.value = service.candidateState.value.copy(
                                pendingEnglishText = "",
                                associationCandidates = emptyList()
                            )
                        }
                    } else if (candState.isComposing) {
                        if (candState.candidates.isNotEmpty()) {
                            selectCandidateAsync(candState.spaceCandidateIndex(state.inputProfile), snapshot = candState)
                        } else {
                            val input = candState.inputText
                            if (input.isNotEmpty()) {
                                withEditor {
                                    service.commitText(input)
                                    service.candidateState.value = service.candidateState.value.copy(
                                        inputText = "",
                                        preeditText = "",
                                        isComposing = false,
                                        pendingEnglishText = "",
                                        candidates = emptyList(),
                                        candidateComments = emptyList(),
                                        associationCandidates = emptyList(),
                                        candidateActions = emptyList()
                                    )
                                }
                                service.rimeEngine.clearComposition()
                                // T9模式：清空partialCommit累积文本，避免下一轮输入
                                // preedit中残留上一轮的提交内容（如"看"→下一轮"看jihua"）
                                if (isT9Schema(state.currentSchemaId)) {
                                    withEditor {
                                        service.t9PartialSegments.clear()
                                        service.keyboardCallbacks?.onT9CompositionCleared?.invoke()
                                        service.uiState.value = service.uiState.value.copy(
                                            t9RightCandidateSelectedCount = 0,
                                            t9SelectedCandidatePinyin = ""
                                        )
                                    }
                                }
                                needsUIUpdate = true
                            }
                        }
                    } else if (!state.isAsciiMode &&
                        hardwareEvent == null && SettingsPreferences.isSpaceCommitAssociationEnabled(service) &&
                        candState.associationCandidates.isNotEmpty()
                    ) {
                        // 空格上屏联想候选（中文模式 + 开关开启 + 联想候选存在）：上屏第一个联想词。
                        // 仅中文联想——英文模式此分支不生效，空格保持上屏空格字符；
                        // 连续联想模式：commitText 会自动触发下一轮推理（一直上屏一直推理）；
                        // 单次联想模式：先置抑制标志，上屏引发的那轮推理被跳过并清空联想候选。
                        val association = candState.associationCandidates.first()
                        withEditor {
                            if (SettingsPreferences.isSingleAssociationMode(service)) {
                                service.predictionManager.suppressNextPredictionOnce()
                            }
                            service.commitText(association)
                        }
                    } else {
                        withEditor {
                            service.commitText(service.textCommit.keyboardLiteral(" "))
                        }
                    }
                }
                "word_separator" -> {
                    if (candState.isComposing || candState.inputText.isNotEmpty()) {
                        val result = service.rimeEngine.processQueuedKeyAndGetResult(0x27, 0)
                        if (result.processed) {
                            sendTransformedResult(result)
                        } else {
                            needsUIUpdate = true
                        }
                    } else {
                        needsUIUpdate = true
                    }
                }
                "shift" -> {
                }
                "mode_change" -> {
                }
                "ime_switch" -> {
                    // Preserve the existing immediate ASCII preview for the default behavior.
                    // A configured cycle/pair may select another native language and cannot invert ASCII.
                    val switchOptions = com.kingzcheung.xime.settings.LanguageSwitchPreferences.forRequest(
                        com.kingzcheung.xime.settings.LanguageSwitchPreferences.read(service), languageSwitchMode)
                    if (switchOptions.mode ==
                        com.kingzcheung.xime.settings.LanguageSwitchMode.CURRENT_ENGLISH) {
                        val state = service.uiState.value
                        val schemaId = service.rimeEngine.getCurrentSchema()
                        val nativeLanguageEnabled = com.kingzcheung.xime.settings.InputModes.languageOf(
                            schemaId, state.schemas) in com.kingzcheung.xime.settings.LanguagePreferences.enabled(service)
                        val optimisticTarget = if (nativeLanguageEnabled) !state.isAsciiMode else true
                        withEditor {
                            service.uiState.value = service.uiState.value.copy(isAsciiMode = optimisticTarget)
                            service.keyboardViewModel.dispatch(
                                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(optimisticTarget, schemaId)
                            )
                        }
                    }
                    // 在 key-processing 线程上执行切换：toggleAsciiMode 阻塞等待 rimeLock
                    // （部署/维护持锁时排队，完成后自动切换），不在主线程阻塞避免 ANR。
                    val t0 = System.nanoTime()
                    FileLogger.i(XimeInputMethodService.TAG, "ime_switch dispatched, ui ascii=${service.uiState.value.isAsciiMode}, thread=${Thread.currentThread().name}")
                    if (!service.schemaController.switchInputMethod(useLanguageKeyPreference = true,
                            languageSwitchMode = languageSwitchMode)) {
                        // 失败后继续显示引擎的实际状态。
                        withEditor {
                            val actualAscii = service.rimeEngine.isAsciiMode()
                            service.uiState.value = service.uiState.value.copy(isAsciiMode = actualAscii)
                            service.keyboardViewModel.dispatch(
                                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(actualAscii, service.rimeEngine.getCurrentSchema())
                            )
                        }
                    }
                    FileLogger.i(XimeInputMethodService.TAG, "ime_switch handled, total ${(System.nanoTime() - t0) / 1_000_000}ms (queue+rimeLock+main)")
                }
                "abc" -> {
                    service.calculatorEngine.clear()
                    updateCalculatorCandidates()
                }
                "number", "common_symbol" -> {
                    // Number/CommonSymbol 内部切换由 KeyboardView 的 key handler 处理
                }
                "emoji" -> {
                    withEditor {
                        service.commitText("😊")
                    }
                }
                else -> {
                    val isNumberKeyboard = service.keyboardViewModel.keyboardState.value is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.Number
                    val isCommonSymbolKeyboard = service.keyboardViewModel.keyboardState.value is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.CommonSymbol

                    val routeResult = com.kingzcheung.xime.calculator.routeCalculatorKey(
                        key = key,
                        isNumberKeyboard = isNumberKeyboard,
                        isCommonSymbolKeyboard = isCommonSymbolKeyboard,
                        calculatorEngine = service.calculatorEngine,
                    )
                    if (routeResult is com.kingzcheung.xime.calculator.CalculatorRouteResult.Handled) {
                        withEditor { service.commitText(service.textCommit.keyboardLiteral(routeResult.commitText, numberPanel = isNumberKeyboard)) }
                        if (isNumberKeyboard) updateCalculatorCandidates()
                        needsUIUpdate = true
                        return@command
                    }

                    val pendingEnglish = candState.pendingEnglishText
                    
                    // 非计算器键清除计算器状态
                    if (!key.matches(Regex("[0-9]")) && key !in listOf("+", "-", "*", "/", ".")) {
                        if (service.calculatorEngine.isActive() || service.calculatorEngine.getCandidate() != null) {
                            service.calculatorEngine.clear()
                            updateCalculatorCandidates()
                        }
                    }
                    
                    // 计算器模式：追踪数字、运算符和小数点
                    if (key.matches(Regex("[0-9]")) || key in listOf("+", "-", "*", "/", ".")) {
                        if (key.matches(Regex("[0-9]")) || key == ".") {
                            service.calculatorEngine.handleDigit(key)
                        } else {
                            service.calculatorEngine.handleOperator(key)
                        }
                        updateCalculatorCandidates()
                    }
                    
                    // 数字选词拦截（插件候选变换存在时）：数字键不进入引擎——rime 会自行选
                    // 引擎候选并返回 committedText，绕过插件候选；映射为对应位置显示候选的
                    // 点击（selectCandidateAsync 按 candidateActions 分流上屏）。
                    // actions 为空（无变换）时保持原生数字选词行为不变。
                    if (!state.isAsciiMode && candState.isComposing &&
                        key.length == 1 && key[0] in '1'..'9'
                    ) {
                        val actions = service.candidateState.value.candidateActions
                        val digitIndex = key[0] - '1'
                        if (actions.isNotEmpty() && digitIndex < service.candidateState.value.candidates.size) {
                            selectCandidateAsync(digitIndex)
                            return@command
                        }
                    }

                    // 所有按键统一经过 Rime 引擎
                    // 字母键不进入此分支（即使 pendingEnglish 非空），需要继续积累编码
                    // 英文模式（isAsciiMode）下非字母键（QWERTY 上滑的数字/符号、数字/符号面板）
                    // 直接上屏，不进入 Rime 引擎与 pendingEnglish 累积（上滑字符即输即上）。
                    if ((state.isAsciiMode || pendingEnglish.isNotEmpty()) && !key.matches(Regex("[a-zA-Z]"))) {
                        val finalKey = key
                        withEditor {
                            // 直接上屏模式：pendingEnglish 对应字符已逐字落盘，只提交增量键值。
                            service.commitText(finalKey)
                            service.candidateState.value = service.candidateState.value.copy(
                                pendingEnglishText = "",
                                associationCandidates = emptyList()
                            )
                        }
                    } else {
                        val isChinese = !state.isAsciiMode
                        val char = key
                        val japaneseCase = JapaneseTyping.usesKanaCase(state.currentSchemaId, state.isAsciiMode)
                        val keyCode = if (japaneseCase) JapaneseTyping.keyCode(key, isShifted) else key.lowercase()[0].code
                        val mask = if (isShifted && !japaneseCase) KeyEvent.META_SHIFT_ON else 0
                        val isLetter = key.matches(Regex("[a-zA-Z]"))
                        val isShiftedChinese = isShifted && isChinese && isLetter && !japaneseCase

                        // 非 ASCII 可打印字符（全角符号/中文标点）：直接上屏，不进入 Rime 引擎。
                        // Rime processKey 只接受标准键码，全角键码（如 U+FF0F）无法识别会被静默
                        // 丢弃，导致中文模式下符号面板点击全角字符无输出（与 Trime onText 行为一致）。
                        if (char.isNotEmpty() && char.any { it.code > 0x7E }) {
                            committedText = char
                            needsUIUpdate = true
                        } else if (isShifted && !isLetter) {
                            if (char.length == 1) {
                                val charCode = char[0].code
                                val processed = service.rimeEngine.processKey(charCode, 0)
                                if (processed) {
                                    val result = service.rimeEngine.getProcessResult(processed)
                                    // commitText 不走 CONFLATED channel：channel 会覆盖丢弃未消费事件，
                                    // 快速打字时中间的 commitText 会被吞（吃键）。
                                    if (result.committedText.isNotEmpty()) {
                                        withEditor { service.commitText(result.committedText) }
                                    }
                                    sendTransformedResult(result) { if (service.calculatorEngine.isActive()) updateCalculatorCandidates() }
                                } else {
                                    committedText = char
                                    needsUIUpdate = true
                                }
                            } else {
                                committedText = char
                                needsUIUpdate = true
                            }
                        } else {
                            // 注：T9 数字键不经过此处（T9KeyboardLayout 直接调
                            // controller.onDigitPressed → applyComposition）。
                            val result = service.rimeEngine.processQueuedKeyAndGetResult(keyCode, mask)
                            if (result.processed) {
                                if (isShiftedChinese && result.committedText != char) {
                                    service.rimeEngine.clearComposition()
                                    committedText = char
                                    needsUIUpdate = true
                                } else {
                                    val committed = result.committedText
                                    if (state.isAsciiMode && committed.isNotEmpty() && result.inputText.isEmpty() && result.candidates.isEmpty()) {
                                        val current = candState.pendingEnglishText
                                        val newPending = current + committed
                                        service.candidateState.value = service.candidateState.value.copy(pendingEnglishText = newPending)
                                        // 直接上屏模式：只提交 Rime 返回的增量字符（如智能引号转换结果），
                                        // 整段 pending 对应的文本已逐字上屏，不可重复提交。
                                        withEditor {
                                            service.commitText(committed)
                                        }
                                        sendTransformedResult(result) { if (service.calculatorEngine.isActive()) updateCalculatorCandidates() }
                                    } else {
                                        if (committed.isNotEmpty()) {
                                            withEditor { service.commitText(committed) }
                                        }
                                        sendTransformedResult(result) { if (service.calculatorEngine.isActive()) updateCalculatorCandidates() }
                                    }
                                }
                            } else {
                                val isAscii = state.isAsciiMode
                                if (!candState.isComposing || isShiftedChinese) {
                                                    if (isAscii) {
                                                        val charToCommit = if (isShifted) char.uppercase() else char.lowercase()
                                                        val currentPending = candState.pendingEnglishText
                                                        val newPending = currentPending + charToCommit
                                                        // 英文直接上屏模式：字符不经 composing region，即输即落盘；
                                                        // pendingEnglishText 仅作编码记录（供联想与选中候选后回删替换校验）。
                                                        withEditor {
                                                            service.commitText(charToCommit)
                                                        }
                                                        service.candidateState.value = service.candidateState.value.copy(
                                                            pendingEnglishText = newPending,
                                                            associationCandidates = emptyList(),
                                                            englishReplaceSupported = service.supportsEnglishCandidateReplace()
                                                        )
                                                        needsUIUpdate = true
                                    } else {
                                        committedText = char
                                        needsUIUpdate = true
                                    }
                                } else {
                                    val candidateText = if (service.rimeEngine.selectCandidate(0)) {
                                        service.rimeEngine.commit()
                                    } else {
                                        ""
                                    }
                                    committedText = candidateText + char
                                    needsUIUpdate = true
                                }
                            }
                        }
                    }
                }
            }
            
            if (needsUIUpdate) {
                val result = pendingResult
                val textToCommit = committedText
                if (result != null) {
                    if (textToCommit != null) {
                        withEditor { service.commitText(textToCommit) }
                    }
                    sendTransformedResult(result) {
                        if (service.calculatorEngine.isActive()) {
                            updateCalculatorCandidates()
                        }
                    }
                } else {
                    val capturedInputText = service.rimeEngine.getInput()
                    val capturedCandidates = service.rimeEngine.getCandidatesWithComments()
                    val capturedIsAscii = service.rimeEngine.isAsciiMode()
                    val capturedHasNext = service.rimeEngine.hasNextPage()
                    val capturedHasPrev = service.rimeEngine.hasPrevPage()
                    // 候选词变换（hotPath 插件能力）：内联刷新路径（无 RimeProcessResult），
                    // key-processing 线程同步调用；ascii 场景不变换（调用点语义与 transformFor 一致）
                    val transformed = if (capturedInputText.isNotEmpty() && !capturedIsAscii) {
                        service.candidateTransform.transform(
                            capturedInputText, "", capturedCandidates.toList(), capturedIsAscii
                        )
                    } else {
                        null
                    }
                    val displayCandidates: List<com.kingzcheung.xime.rime.RimeCandidate> =
                        transformed?.candidates ?: capturedCandidates.toList()
                    if (textToCommit != null) {
                        withEditor { service.commitText(textToCommit) }
                    }
                    publishUi {
                        val pendingEnglish = service.candidateState.value.pendingEnglishText
                        val (filteredTexts, filteredComments) = if (capturedIsAscii) {
                            val filtered = capturedCandidates.filterNot { candidate ->
                                candidate.text.any { it.code in 0x4E00..0x9FFF }
                            }
                            filtered.map { it.text } to filtered.map { it.comment }
                        } else {
                            displayCandidates.map { it.text } to displayCandidates.map { it.comment }
                        }
                        // 秘密输入框（密码/终端）禁英文联想：联想会泄漏输入前缀，
                        // 回删替换也会破坏受限宿主的输入。NO_SUGGESTIONS 只是宿主
                        // 不要内联补全，候选栏联想仍提供（与退格/applyComposition 路径同口径）。
                        val secret = service.isSecretEditor()
                        service.candidateState.value = service.candidateState.value.copy(
                            inputText = capturedInputText,
                            candidates = filteredTexts,
                            candidateComments = filteredComments,
                            isComposing = capturedInputText.isNotEmpty(),
                            associationCandidates = if (secret || ((capturedIsAscii || !service.isChineseMode) && pendingEnglish.isEmpty())) emptyList() else service.candidateState.value.associationCandidates,
                            isShowingRecentClipboard = false,
                            hasNextPage = capturedHasNext,
                            hasPrevPage = capturedHasPrev,
                            candidateActions = transformed?.actions ?: emptyList()
                        )
                        if (capturedIsAscii != service.uiState.value.isAsciiMode) {
                            FileLogger.i(XimeInputMethodService.TAG, "keyRouter UI refresh: ascii ${service.uiState.value.isAsciiMode}->$capturedIsAscii")
                        }
                        service.uiState.value = service.uiState.value.copy(isAsciiMode = capturedIsAscii)
                        if (pendingEnglish.isNotEmpty() && !secret && service.supportsEnglishCandidateReplace()) {
                            service.serviceScope.launch(InputCommandOwner.requireOwner().context()) {
                                val candidates = service.predictionManager.getEnglishAssociations(pendingEnglish, PredictionManager.MAX_ASSOCIATION_COUNT)
                                withEditor {
                                    val current = service.candidateState.value
                                    // 在途联想过期校验：pendingEnglish 已变/已清 → 丢弃迟到回填
                                    if (current.pendingEnglishText == pendingEnglish) {
                                        service.candidateState.value = current.copy(associationCandidates = candidates)
                                    }
                                }
                            }
                        }
                        if (service.calculatorEngine.isActive()) {
                            updateCalculatorCandidates()
                        }
                    }
                }
            }
        }
    }

    /** Returns false only when Space should keep the ordinary on-screen-key behavior. */
    private suspend fun processHardwareCandidateKey(event: KeyEvent, pageAtBoundary: Boolean = false): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> HardwareCandidateKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> HardwareCandidateKey.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> HardwareCandidateKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> HardwareCandidateKey.DOWN
            KeyEvent.KEYCODE_ESCAPE -> HardwareCandidateKey.CANCEL
            KeyEvent.KEYCODE_SPACE -> HardwareCandidateKey.SPACE
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> HardwareCandidateKey.ENTER
            else -> if (hardwareCandidateDigitIndex(event.keyCode, event.isShiftPressed) != null)
                HardwareCandidateKey.DIGIT else return false
        }
        val arrow = key == HardwareCandidateKey.LEFT || key == HardwareCandidateKey.RIGHT ||
            key == HardwareCandidateKey.UP || key == HardwareCandidateKey.DOWN
        hardwareNavigation.startPress(key, repeat = event.repeatCount > 0)

        // This read waits for the engine lock and never consumes commit text. In
        // particular, a rapid letter followed by an arrow cannot see an old empty UI.
        val composition = service.rimeEngine.readQueuedComposition()
        if (composition != null &&
            service.candidateState.value.engineRevision != composition.engineRevision) {
            val transformed = service.candidateTransform.transform(composition.input, composition.preedit,
                composition.candidates.toList(), composition.isAsciiMode)
            val injections = if (isT9Schema(service.uiState.value.currentSchemaId)) {
                service.candidateTransform.transformForT9(com.kingzcheung.xime.rime.RimeProcessResult(
                    true, "", composition.input, composition.preedit, composition.candidates,
                    composition.isAsciiMode, composition.hasNextPage, composition.hasPrevPage,
                    engineRevision = composition.engineRevision,
                )).orEmpty()
            } else emptyList()
            withEditor {
                if (service.rimeEngine.isCandidateRevisionCurrent(composition.engineRevision)) {
                    service.sessionController.applyComposition(
                        transformed?.let { composition.copy(candidates = it.candidates.toTypedArray()) } ?: composition,
                        transformed?.actions ?: emptyList(), injections,
                    )
                }
            }
        }
        val snapshot = withEditor {
            val ui = service.uiState.value
            hardwareCandidateSnapshot(service.candidateState.value,
                ui.isAsciiMode || ui.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.ENGLISH,
                service.predictionManager.hasPendingPrediction)
        }
        val engineHasInput = composition
            ?.takeIf { service.rimeEngine.isCandidateRevisionCurrent(it.engineRevision) }
            ?.let { it.input.isNotEmpty() || service.t9PartialSegments.isNotEmpty() }
        val decision = hardwareNavigation.decide(key, snapshot, engineHasInput, pageAtBoundary,
            digitIndex = hardwareCandidateDigitIndex(event.keyCode, event.isShiftPressed))
        if (com.kingzcheung.xime.BuildConfig.DEBUG) {
            Log.d("HardwareNavigation", "key=$key decision=$decision engineInput=$engineHasInput " +
                "words=${snapshot.words.size} compact=${service.uiState.value.isCompact} " +
                "revision=${snapshot.state.engineRevision}")
        }
        if (decision != HardwareCandidateDecision.Host) hardwareNavigation.retainPress(key)
        when (decision) {
            is HardwareCandidateDecision.Highlight -> withEditor { publishHardwareCandidateSelection() }
            is HardwareCandidateDecision.Page -> {
                if (snapshot.expanded) {
                    withEditor {
                        service.expandedPageScroll(decision.direction)
                        publishHardwareCandidateSelection()
                    }
                } else {
                    if (decision.direction < 0) service.rimeEngine.pageUp() else service.rimeEngine.pageDown()
                    withEditor {
                        publishHardwareCandidateSelection()
                        service.updateUI()
                    }
                }
            }
            is HardwareCandidateDecision.Confirm -> {
                if (decision.association) {
                    withEditor {
                        val current = hardwareCandidateSnapshot(service.candidateState.value, snapshot.english,
                            service.predictionManager.hasPendingPrediction)
                        if (snapshot.sameSource(current)) {
                            selectAssociation(decision.index)
                        }
                    }
                } else if (snapshot.expandedGlobalIndices != null) {
                    selectCandidateGlobalAsync(snapshot.expandedGlobalIndices[decision.index], snapshot.state)
                    withEditor { publishHardwareCandidateSelection() }
                } else {
                    selectCandidateAsync(decision.index, snapshot = snapshot.state)
                    withEditor { publishHardwareCandidateSelection() }
                }
            }
            HardwareCandidateDecision.Cancel -> {
                // Japanese conversion is separate from Rime composition. Cancel it
                // before clearing input; already committed English text is untouched.
                service.japaneseInputController.handleKey("clear_composition")
                clearInputStateForKeys()
                withEditor {
                    publishHardwareCandidateSelection()
                    service.maybeCollapseCandidatePage()
                }
            }
            HardwareCandidateDecision.Host -> {
                if (!arrow || !hardwareNavigation.ownsPress(key)) {
                    withEditor {
                        if (arrow) {
                            // Moving in the editor invalidates the word context, including a
                            // pending result and suggestions hidden by a narrow/vertical bar.
                            service.predictionManager.invalidatePendingPredictions()
                            val current = service.candidateState.value
                            service.candidateState.value = current.copy(associationCandidates = emptyList(),
                                pendingEnglishText = "")
                            hardwareNavigation.clear()
                            publishHardwareCandidateSelection()
                        }
                        service.currentInputConnection?.sendKeyEvent(event)
                        service.currentInputConnection?.sendKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
                    }
                }
            }
            HardwareCandidateDecision.Consume -> Unit
            HardwareCandidateDecision.DefaultInput -> {
                if (!snapshot.state.isComposing && snapshot.state.inputText.isEmpty()) {
                    withEditor {
                        service.predictionManager.invalidatePendingPredictions()
                        service.candidateState.value = service.candidateState.value.copy(associationCandidates = emptyList())
                        hardwareNavigation.clear()
                        publishHardwareCandidateSelection()
                    }
                }
                return false
            }
        }
        return true
    }

    /** Main-thread commit shared by touch and physical keys; rejection retains the selection. */
    internal fun selectAssociation(index: Int): Boolean {
        val state = service.candidateState.value
        service.predictionManager.invalidatePendingPredictions()
        val accepted = commitAssociationSelection(state, index,
            commit = { text ->
                service.commitTextAndPredict(text, isPaste = false,
                    allowPrediction = !SettingsPreferences.isSingleAssociationMode(service)).accepted
            },
            replace = service::replaceBeforeCursor)
        if (!accepted) return false
        service.candidateState.value = service.candidateState.value.copy(
            pendingEnglishText = "", associationCandidates = emptyList())
        hardwareNavigation.clear()
        publishHardwareCandidateSelection()
        return true
    }

    /** Each tap keeps its FIFO position; a hold allows only one outstanding repeat. */
    internal fun handleDeleteKey() {
        // Keep this physical action at its original position, including D-D-A and D-A-D.
        val hold = com.kingzcheung.xime.keyboard.RepeatInput.current.get()
        if (hold != null && !hold.acquire()) return
        postRimeJob {
            try { if (!hasPendingCandidateCommit && hold?.isActive != false) processDeleteKey() }
            finally { hold?.completed() }
        }
    }

    /** 单次退格处理（service.keyProcessingDispatcher 上执行）。 */
    internal suspend fun processDeleteKey() {
        val owner = service.uiState.value.inputSessionId
        // 快捷发送表单显示：退格按焦点路由到表单内 EditText（与单击退格同一路径），不进入 Rime
        if (service.uiState.value.showQuickSendForm) {
            withEditor {
                if (owner != service.uiState.value.inputSessionId) return@withEditor
                service.deleteInQuickSendForm()
            }
            return
        }
        if (service.japaneseInputController.deleteKana()) return
        val candState = service.candidateState.value
        // 退格改变输入上下文：使在途的联想预测结果失效，防止过期结果迟到回填
        // associationCandidates，导致长按退格删除时候选栏在"联想词↔空"之间闪动。
        val predictionWasPending = service.predictionManager.invalidatePendingPredictions()
        val target = deletionTarget(
            engineComposing = service.rimeEngine.compositionActiveForDeletion(),
            uiComposing = candState.isComposing || candState.inputText.isNotEmpty() || candState.preeditText.isNotEmpty(),
            partialSegments = service.t9PartialSegments.isNotEmpty(),
            pendingEnglish = candState.pendingEnglishText.isNotEmpty(),
            candidatesVisible = candState.associationCandidates.isNotEmpty() || candState.isShowingRecentClipboard,
            predictionPending = predictionWasPending,
        )
        if (target == DeletionTarget.WAIT) return
        // 计算器模式：追踪退格
        val calculatorWasActive = service.calculatorEngine.isActive()
        service.calculatorEngine.handleDelete()
        // Only replace candidates owned by the calculator. Clearing ordinary pinyin
        // candidates here momentarily empties the list and collapses its expanded page.
        if (calculatorWasActive) updateCalculatorCandidates()

        // 数字/符号键盘：直接发送系统退格，不经过 Rime
        // 防止 T9 残留状态被 Rime 退格修改导致 UI 不一致
        val layoutState = service.keyboardViewModel.keyboardState.value
        if ((layoutState is KeyboardLayoutState.Number || layoutState is KeyboardLayoutState.Symbol) &&
            target == DeletionTarget.DOCUMENT) {
            withEditor {
                if (owner != service.uiState.value.inputSessionId) return@withEditor
                service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        } else when {
            // 1. 英文待处理文本：逐个删除字符，重新加载联想
            target == DeletionTarget.ENGLISH -> {
                val newPending = candState.pendingEnglishText.dropLast(1)
                if (newPending.isNotEmpty()) {
                    withEditor {
                        if (owner != service.uiState.value.inputSessionId) return@withEditor
                        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        service.candidateState.value = service.candidateState.value.copy(
                            pendingEnglishText = newPending,
                            candidates = emptyList(),
                            candidateComments = emptyList(),
                            associationCandidates = emptyList(),
                            candidateActions = emptyList()
                        )
                    }
                    if (!service.isSecretEditor() && service.supportsEnglishCandidateReplace()) {
                        service.serviceScope.launch(InputCommandOwner.requireOwner().context()) {
                            val candidates = service.predictionManager.getEnglishAssociations(newPending, PredictionManager.MAX_ASSOCIATION_COUNT)
                            withEditor {
                                if (owner != service.uiState.value.inputSessionId) return@withEditor
                                if (service.candidateState.value.pendingEnglishText == newPending) {
                                    service.candidateState.value = service.candidateState.value.copy(associationCandidates = candidates)
                                }
                            }
                        }
                    }
                } else {
                    withEditor {
                        if (owner != service.uiState.value.inputSessionId) return@withEditor
                        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        service.candidateState.value = service.candidateState.value.copy(
                            pendingEnglishText = "",
                            candidates = emptyList(),
                            candidateComments = emptyList(),
                            associationCandidates = emptyList(),
                            isShowingRecentClipboard = false,
                            candidateActions = emptyList()
                        )
                    }
                    // 待确认英文已删空：候选展开页若开着则收起（无内容可展示）
                    service.maybeCollapseCandidatePage()
                }
            }

            // 2. Rime 编码中：让 Rime 处理退格，更新候选
            target == DeletionTarget.COMPOSITION -> {
                val result = service.rimeEngine.deleteCompositionAndGetResult()
                if (owner != service.uiState.value.inputSessionId) return
                if (result.inputText.isEmpty()) {
                    // T9 部分提交：剩余编码删完后，已上屏/ composing 的部分候选词无法用
                    // RIME 退格删除，会一直卡在候选栏。这里撤销最近一次部分提交：
                    // 清空自己持有的 composing 区域并从累积列表移除，不删除宿主正文。
                    if (service.t9PartialSegments.isNotEmpty()) {
                        // 已撤销段只存在于输入法状态（候选栏/输入框 composing 的显示），
                        // 从未上屏到正文：候选栏模式不能按段文本长度删宿主文本，
                        // 否则会误删光标前的正文（2026-09-25 真机实证）。
                        val removed = service.t9PartialSegments.rollbackPartialSegments(1)
                        withEditor {
                            if (owner != service.uiState.value.inputSessionId) return@withEditor
                            if (SettingsPreferences.getInputTextLocation(service)
                                == SettingsPreferences.INPUT_TEXT_INPUT_BOX) {
                                service.endComposingInputBox()
                            }
                            if (removed.isNotEmpty()) {
                                service.uiState.value = service.uiState.value.copy(
                                    t9RightCandidateSelectedCount =
                                        (service.uiState.value.t9RightCandidateSelectedCount - removed.size)
                                            .coerceAtLeast(0L),
                                    t9SelectedCandidatePinyin = ""
                                )
                            }
                        }
                        // undo 联动：撤销段时回滚用户词典调频（按段，不按字符）。
                        // Undo changes composition only; no user dictionary write.
                    }
                }
                sendTransformedResult(result) { if (service.calculatorEngine.isActive()) updateCalculatorCandidates() }
            }

            // 3. 联想词或剪贴板：仅清空候选栏，不回删已上屏字符
            target == DeletionTarget.CANDIDATES -> {
                service.candidateState.value = service.candidateState.value.copy(
                    candidates = emptyList(),
                    candidateComments = emptyList(),
                    associationCandidates = emptyList(),
                    isShowingRecentClipboard = false
                )
                // 候选展开页：联想/剪贴板候选清空后无内容，收起
                service.maybeCollapseCandidatePage()
            }

            // 4. 无候选也无编码：直接回删已上屏文本
            else -> {
                service.predictionManager.deleteLastChar()

                withEditor {

                    if (owner != service.uiState.value.inputSessionId) return@withEditor
                    service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }

                service.candidateState.value = service.candidateState.value.copy(
                    candidates = emptyList(),
                    candidateComments = emptyList(),
                    associationCandidates = emptyList(),
                    isShowingRecentClipboard = false
                )
                // 候选展开页：无候选无编码，收起
                service.maybeCollapseCandidatePage()
            }
        }
    }

    /**
     * Posts a rime operation to the shared input FIFO for sequential execution.
     * Ensures no interleaving with key processing.
     */
    internal fun postRimeJob(block: suspend CoroutineScope.() -> Unit) {
        val owner = InputCommandOwner.current.get() ?: run {
            check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                "A background input job cannot acquire the current editor"
            }
            commandOwner(service.inputReadiness.ticket() ?: return)
        }
        service.inputCommands.submit(owner.context()) {
            owner.requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
            block()
        }
    }

    suspend fun selectCandidateAsync(index: Int, expandedCandidate: RimeCandidate? = null, snapshot: CandidateState = service.candidateState.value) {
        InputCommandOwner.requireOwner().requireCurrent(service.inputReadiness, service.uiState.value.inputSessionId)
        if (hasPendingCandidateCommit) return
        if (!snapshot.hasSameSelectionSource(service.candidateState.value)) return
        if (service.japaneseInputController.commit(index)) return
        if (!service.rimeEngine.isCandidateRevisionCurrent(snapshot.engineRevision)) return
        // 展开页点选（expandedCandidate 非空）：取词/注释以展开页显示的同一份
        // 全量列表为准（所见即所得）。index 是全量索引，不得用于索引引擎当前页的
        // candidates/candidateComments——部分选拼音（SELECTION 态）或候选超页时
        // 两列表错位，全局索引套在当前页上会取错词（如点长词只上屏另一短词）。
        // 插件候选 actions 按页内索引记录，展开页索引同样不适用，直接跳过检测。
        val pendingAction = if (expandedCandidate == null) {
            snapshot.candidateActions.getOrNull(index)
        } else {
            null
        }
        if (pendingAction != null && pendingAction.isPluginCandidate) {
            // 防御：中英/方案切换后引擎组合已清空但 candidateState 残留旧候选+actions，
            // 此时点选必须回落原生路径（引擎侧 selectCandidate 失败自动防呆，与旧行为一致），
            // 否则残留插件候选会绕过引擎校验直接上屏。
            val engineHasComposition = service.rimeEngine.getInput().isNotEmpty() ||
                service.rimeEngine.getCandidates().isNotEmpty()
            if (engineHasComposition) {
                commitSelectedText(pendingAction.commitText)
                return
            }
        }

        val selectedCandidate = expandedCandidate?.text
            ?: if (index < snapshot.candidates.size) {
                snapshot.candidates[index]
            } else null

        val isT9 = isT9Schema(service.uiState.value.currentSchemaId)
        val candidatePinyin = if (isT9) {
            expandedCandidate?.comment
                ?: if (index < snapshot.candidateComments.size) {
                    snapshot.candidateComments[index]
                } else {
                    null
                }
        } else {
            null
        }

        // 在 RIME 真正 select/commit 之前，先同步通知 T9 控制器消费数字。
        // 控制器返回 true 表示输入序列已被该候选词完整消费，服务层应视为 full commit。
        // 传入候选文本用于 C++ (comment, text) 双条件精确定位（注释歧义防错码）。
        val candidateTextLength = selectedCandidate?.length ?: 0
        val fullyConsumed = if (isT9) {
            service.keyboardCallbacks?.onT9RightCandidateWillBeSelected?.invoke(candidatePinyin, selectedCandidate, candidateTextLength, snapshot.engineRevision) ?: return
        } else {
            false
        }

        // T9：跳过 service.rimeEngine.selectCandidate，消费已由 T9 处理器（t9_processor）
        // 独立完成。selectCandidate 会遗留 [confirmed, phony] 残留 composition 状态，
        // 导致后续 forceSendToRime 的 setInput 无法正常重建候选项（对齐 main 分支）。
        // 非 T9 才调用 selectCandidate，并用 resolveRimeCandidateIndex 修正候选 index，
        // 避免 UI 候选被过滤/重排后上屏错词。
        val selection = if (isT9) {
            null
        } else {
            val rimeIndex = if (pendingAction != null && pendingAction.engineIndex >= 0) {
                // 变换映射记录的引擎候选索引（比按显示文本回查更准）
                pendingAction.engineIndex
            } else if (selectedCandidate != null) {
                resolveRimeCandidateIndex(index, selectedCandidate, service.rimeEngine.getCandidates().toList())
            } else {
                index
            }
            service.rimeEngine.selectCandidateAtRevision(rimeIndex, snapshot.engineRevision) ?: return
        }

        // T9：跳过 service.rimeEngine.commit()，用用户点选的 selectedCandidate 作为权威上屏文本。
        val committedText = selection?.committedText.orEmpty()
        // T9 模式下 fullyConsumed 是判断 full/partial commit 的唯一权威：
        // 当控制器明确 partial commit 时，即使 RIME commit() 返回非空文本
        // （RIME 内部做了 partial commit），也不应走 full commit 路径。
        val isFullCommit = if (isT9) {
            fullyConsumed && selectedCandidate != null
        } else {
            committedText.isNotEmpty()
        }
        if (isFullCommit) {
            // T9 full commit：以用户点选的候选词文本为权威上屏文本；
            // 非 T9 仍用 RIME committedText（可能含简繁转换等处理）。
            val textToMerge = if (isT9 && selectedCandidate != null) {
                selectedCandidate
            } else if (committedText.isNotEmpty()) {
                committedText
            } else {
                selectedCandidate!!
            }
            // T9 full commit：partial commit 累积文本（未单独上屏，只存在于
            // service.t9PartialSegments 的 composing 区）与本次上屏文本是独立词，必须拼接，
            // 不能去重（mergePartialCommitText 仅用于显示，会吞掉重复的词）。
            // 但若点击的是 partial 词本身（RIME 无菜单、候选栏显示的就是 partial 词，
            // 无拼音注释 candidatePinyin==null），则只提交累积的 partial 文本，避免重复。
            val partialTexts = service.t9PartialSegments.map { it.text }
            val fullCommitText = if (isT9 && partialTexts.isNotEmpty()) {
                if (candidatePinyin == null) {
                    partialTexts.joinToString("")
                } else {
                    partialTexts.joinToString("") + textToMerge
                }
            } else {
                textToMerge
            }
            deliverCandidateCommit(PendingCandidateCommit(
                InputCommandOwner.requireOwner(), fullCommitText, isT9
            ))
        } else {
            withEditor {
                if (isT9) {
                    // partial commit：累积候选文本与拼音，供合并显示与 full commit 调频拼接。
                    if (selectedCandidate != null) {
                        service.t9PartialSegments.add(T9PartialSegment(selectedCandidate, candidatePinyin ?: ""))
                    }
                    // 保留状态字段，供 UI 层感知右侧选词事件
                    service.uiState.value = service.uiState.value.copy(
                        t9RightCandidateSelectedCount = service.uiState.value.t9RightCandidateSelectedCount + 1,
                        t9SelectedCandidatePinyin = candidatePinyin ?: ""
                    )
                } else {
                    service.updateUI()
                }
            }
            // Rebuild remaining code on the worker, within this selection's FIFO slot.
            if (isT9) service.keyboardCallbacks?.onT9ForceSendToRime?.invoke()
        }
    }

    /**
     * 插件候选直接上屏（所见即所得）：不经引擎 select/commit。
     * 先上屏并清空候选状态（与 enter 提交分支同序），再清引擎组合态。
     */
    internal suspend fun commitSelectedText(text: String) {
        deliverCandidateCommit(PendingCandidateCommit(
            InputCommandOwner.requireOwner(), text, false
        ))
    }

    /** A rejected commit retains its original owner and text. Retry is an explicit user action. */
    private suspend fun deliverCandidateCommit(pending: PendingCandidateCommit) {
        pending.deliver(
            submit = { text -> withEditor {
                val previousText = service.predictionManager.lastCommittedText
                val outcome = service.submitText(text)
                if (outcome.accepted) {
                    if (previousText.isNotEmpty() && service.isChineseMode &&
                        SettingsPreferences.isSmartPredictionEnabled(service) && AssociationManager.isInitialized()) {
                        service.predictionManager.recordInputPair(previousText.last().toString(), text)
                    }
                }
                outcome
            } },
            rejected = { withEditor {
                pendingCandidateCommit = it
                service.uiState.value = service.uiState.value.copy(rejectedCommitText = it.text)
            } },
            accepted = {
                // Learning failure is diagnostic; it must never undo accepted text.
                if (it.t9 && !service.rimeEngine.t9MemorizeSelection(it.text)) {
                    FileLogger.w("ImeKeyRouter", "Selected T9 phrase was committed but learning failed (missing captured code or dictionary write failure)")
                }
                service.rimeEngine.clearComposition()
                withEditor {
                    discardPendingCandidateCommit()
                    service.keyboardCallbacks?.onT9CompositionCleared?.invoke()
                    service.t9PartialSegments.clear()
                    service.candidateState.value = service.candidateState.value.copy(
                        inputText = "", preeditText = "", pendingEnglishText = "",
                        candidates = emptyList(), candidateComments = emptyList(),
                        candidateActions = emptyList(), expandedCandidates = emptyList(),
                        expandedCandidatesLoaded = false, isComposing = false,
                        hasNextPage = false, hasPrevPage = false, isShowingRecentClipboard = false,
                    )
                    service.uiState.value = service.uiState.value.copy(
                        t9RightCandidateSelectedCount = 0, t9SelectedCandidatePinyin = ""
                    )
                }
            },
        )
    }
    
    /**
     * 输入态判定（clear_all 与 undo_clear 共用）。
     *
     * 输入态 = 存在未上屏内容：RIME 组合（candidateState 派生字段）或英文输入
     *（pendingEnglishText 非空——英文按键不经 RIME，故 inputText/preeditText/isComposing 恒空，
     * 漏判会让 clear_all 把 composing 区文本与 pendingEnglishText 重复拼接）
     * 或 T9 partial 累积。
     * 不能用 RIME getInput()——tryLocked 锁竞争时静默返回空，会误判空闲态。
     */
    private fun hasInputState(candState: CandidateState): Boolean =
        candState.isComposing ||
            candState.inputText.isNotEmpty() ||
            candState.preeditText.isNotEmpty() ||
            candState.pendingEnglishText.isNotEmpty() ||
            service.t9PartialSegments.isNotEmpty() ||
            service.rimeEngine.compositionActiveForDeletion() != false ||
            candState.associationCandidates.isNotEmpty() || candState.isShowingRecentClipboard ||
            service.predictionManager.hasPendingPrediction

    /**
     * 清空输入态（预编辑/候选/联想/partial 累积/计算器），不动已上屏文本。
     * 供 clear_composition 与 clear_all 输入态分支共用。
     *
     * 显式清 preeditText（与提交路径"残留根治"一致），不依赖 updateUI 自愈；
     * 输入框模式清 composing 区；T9 方案重置左侧候选区（其他键盘无左栏，跳过）。
     */
    private suspend fun clearInputStateForKeys() {
        service.predictionManager.invalidatePendingPredictions()
        service.calculatorEngine.clear()
        updateCalculatorCandidates()
        service.t9PartialSegments.clear()
        service.rimeEngine.clearQueuedComposition()
        service.candidateState.value = service.candidateState.value.copy(
            candidates = emptyList(),
            candidateComments = emptyList(),
            associationCandidates = emptyList(),
            pendingEnglishText = "",
            inputText = "",
            preeditText = "",
            isComposing = false,
            isShowingRecentClipboard = false
        )
        if (SettingsPreferences.getInputTextLocation(service) ==
            SettingsPreferences.INPUT_TEXT_INPUT_BOX
        ) {
            withEditor { service.endComposingInputBox() }
        }
        if (isT9Schema(service.uiState.value.currentSchemaId)) {
            withEditor {
                service.keyboardCallbacks?.onT9CompositionCleared?.invoke()
                service.uiState.value = service.uiState.value.copy(
                    t9RightCandidateSelectedCount = 0,
                    t9SelectedCandidatePinyin = ""
                )
            }
        }
    }

    /**
     * 更新计算器候选栏显示
     * 显示两个候选：
     * - index 0: 计算结果（如 "2"），点击直接替换为结果
     * - index 1: 带公式的结果（如 "1+1=2"），点击显示公式和结果
     */
    internal fun updateCalculatorCandidates() {
        val candidate = service.calculatorEngine.getCandidate()
        val result = service.calculatorEngine.getResult()
        service.candidateState.value = if (candidate != null && result.isNotEmpty()) {
            service.candidateState.value.copy(
                candidates = listOf(result, candidate),
                candidateComments = emptyList()
            )
        } else {
            // 如果计算器之前有显示但现在已清除，也要清空候选栏
            if (service.candidateState.value.candidates.isNotEmpty() && !service.calculatorEngine.isActive()) {
                service.candidateState.value.copy(
                    candidates = emptyList(),
                    candidateComments = emptyList()
                )
            } else {
                service.candidateState.value
            }
        }
    }

    /** Main-thread display-only action. No engine mutation, buffer consumption or host commit. */
    internal fun highlightNextCandidate(snapshot: CandidateState) = highlightCandidate(snapshot, forward = true)

    internal fun highlightPreviousCandidate(snapshot: CandidateState) = highlightCandidate(snapshot, forward = false)

    private fun highlightCandidate(snapshot: CandidateState, forward: Boolean) {
        if (service.inputReadiness.ticket() == null || hasPendingCandidateCommit) return
        val current = service.candidateState.value
        if (!snapshot.hasSameSelectionSource(current) ||
            !service.rimeEngine.isCandidateRevisionCurrent(snapshot.engineRevision)) return
        val profile = service.uiState.value.inputProfile
        service.candidateState.value = if (forward) current.advanceT9Candidate(profile)
            else current.retreatT9Candidate(profile)
    }

    internal fun selectCandidate(index: Int) {
        service.composeViewRef?.let { service.feedbackManager.performKeyPressEffect(view = it) }

        // 计算器模式（仅在数字/常用符号键盘下生效，防止状态残留导致其他键盘候选词点击异常）
        val layoutState = service.keyboardViewModel.keyboardState.value
        val isCalculatorKeyboard = layoutState is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.Number
                || layoutState is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.CommonSymbol
        if (service.calculatorEngine.isActive() && isCalculatorKeyboard) {
            val result = service.calculatorEngine.getResult()
            val expression = service.calculatorEngine.getExpression()
            val formulaResult = service.calculatorEngine.getFormulaResult()
            if (result.isNotEmpty() && expression.isNotEmpty()) {
                val textToCommit: String
                // index 0: 纯结果（如 "2"）
                // index 1: 公式结果（如 "1+1=2"）
                textToCommit = when (index) {
                    0 -> result
                    1 -> formulaResult
                    else -> ""
                }
                if (textToCommit.isNotEmpty()) {
                    service.calculatorEngine.clear()
                    run {
                        val ic = service.currentInputConnection
                        if (ic != null) {
                            // 删除输入框中已键入的表达式
                            ic.deleteSurroundingText(expression.length, 0)
                            // 提交选中的文本
                            ic.commitText(textToCommit, textToCommit.length)
                        }
                        service.candidateState.value = CandidateState()
                    }
                }
            }
            return
        }
        
        if (service.candidateState.value.isShowingRecentClipboard && index >= 0 && index < service.recentClipboardItemsState.value.size) {
            val text = service.recentClipboardItemsState.value[index].text
            service.textCommit.selectClipboardItem(text)
            service.candidateState.value = service.candidateState.value.copy(
                isShowingRecentClipboard = false,
                candidates = emptyList(),
                candidateComments = emptyList()
            )
        } else {
            val snapshot = service.candidateState.value
            postRimeJob {
                selectCandidateAsync(index, snapshot = snapshot)
            }
        }
    }

    /**
     * 九键长按持久屏蔽完整候选；其他方案沿用删除用户学习词的接口。
     * 显示索引经 resolveRimeCandidateIndex 映射回引擎原始索引（插件候选
     * 注入会使两者错位，与 selectCandidateAsync 同口径），删除后刷新候选。
     */
    internal fun deleteCandidate(displayIndex: Int) {
        val snapshot = service.candidateState.value
        postRimeJob {
            if (!snapshot.hasSameSelectionSource(service.candidateState.value)) return@postRimeJob
            val text = snapshot.candidates.getOrNull(displayIndex)
            if (text.isNullOrEmpty()) return@postRimeJob
            val engineIndex = resolveRimeCandidateIndex(
                displayIndex, text, service.rimeEngine.getCandidates().toList()
            )
            val ok = service.rimeEngine.suppressCandidateAtRevision(engineIndex, snapshot.engineRevision)
            FileLogger.i(
                XimeInputMethodService.TAG,
                "DeleteCandidate: text='$text' display=$displayIndex engine=$engineIndex ok=$ok"
            )
            if (ok) {
                withEditor {
                    service.updateUI()
                }
            }
        }
    }
    
    internal fun pageDown() {
        postRimeJob {
            service.rimeEngine.pageDown()
            withEditor {
                service.updateUI()
            }
        }
    }
    
    internal fun pageUp() {
        postRimeJob {
            service.rimeEngine.pageUp()
            withEditor {
                service.updateUI()
            }
        }
    }

    /**
     * 候选展开页点选：按跨页全局索引选词（select_candidate，区别于候选栏的
     * 当前页内索引 select_candidate_on_current_page）。
     *
     * 关键：rime 的 select 只是把候选放进内部 commit 缓冲，宿主必须再调
     * [RimeEngine.commit]（get_commit）拉取文本并自行上屏——只刷新 composition
     * 拿不到上屏内容（编码已消费、字却丢失，即此前"展开页点选无法上屏"的根因）。
     */
    internal fun selectCandidateGlobal(globalIndex: Int) {
        // 点选震动反馈与候选栏同源（performKeyPressEffect），保证手感一致
        service.composeViewRef?.let { service.feedbackManager.performKeyPressEffect(view = it) }
        // 入队前先按全局索引取全量候选：调用线程读到的 expandedCandidates 正是
        // UI 渲染的同一份列表；入队后再取可能被编码刷新清空/重建而扑空
        val snapshot = service.candidateState.value
        postRimeJob {
            selectCandidateGlobalAsync(globalIndex, snapshot)
        }
    }

    private suspend fun selectCandidateGlobalAsync(globalIndex: Int, snapshot: CandidateState) {
            val expandedCandidate = snapshot.expandedCandidates.getOrNull(globalIndex) ?: return
            if (!snapshot.hasSameSelectionSource(service.candidateState.value)) return
            if (service.japaneseInputController.commit(globalIndex)) return
            if (!service.rimeEngine.isCandidateRevisionCurrent(snapshot.engineRevision)) return
            // T9 方案的选词消费由 t9_processor 独立完成，直接调引擎 select 会
            // 遗留 [confirmed, phony] 残留组合态（见 selectCandidateAsync 注释），
            // 降级走候选栏同款路径；取词/注释必须用展开页同一份全量候选——
            // 全局索引与引擎当前页列表错位时会取错词/丢字
            if (isT9Schema(service.uiState.value.currentSchemaId)) {
                selectCandidateAsync(globalIndex, expandedCandidate, snapshot)
                return
            }
            val selection = service.rimeEngine.selectCandidateAtRevision(globalIndex, snapshot.engineRevision, global = true) ?: return
            val committedText = selection.committedText
            if (committedText.isNotEmpty()) {
                deliverCandidateCommit(PendingCandidateCommit(
                    InputCommandOwner.requireOwner(), committedText, false
                ))
            } else {
                // 引擎未产生 commit（选中后继续组句的多段场景）：刷新组合态
                withEditor {
                    service.updateUI()
                }
            }
    }

    /** 展开页使用同一原生候选版本与全局索引执行反馈，再重读候选。 */
    internal fun deleteCandidateGlobal(globalIndex: Int) {
        val snapshot = service.candidateState.value
        postRimeJob {
            if (!snapshot.hasSameSelectionSource(service.candidateState.value)) return@postRimeJob
            val text = snapshot.expandedCandidates.getOrNull(globalIndex)?.text
            if (text.isNullOrEmpty()) return@postRimeJob
            val ok = service.rimeEngine.suppressCandidateAtRevision(globalIndex, snapshot.engineRevision, global = true)
            FileLogger.i(
                "ImeKeyRouter",
                "deleteCandidateGlobal: text='$text' index=$globalIndex ok=$ok"
            )
            if (!ok) return@postRimeJob
            withEditor {
                service.updateUI()
            }
        }
    }

}
