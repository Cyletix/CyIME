package com.kingzcheung.xime.service

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.ui.keyboard.isHandwritingSchema
import com.kingzcheung.xime.ui.keyboard.JapaneseKanaAction
import com.kingzcheung.xime.ui.keyboard.japaneseKanaRomaji
import com.kingzcheung.xime.rime.RimeProcessResult
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputMode
import com.kingzcheung.xime.settings.InputModes
import com.kingzcheung.xime.settings.InputProfileSelection
import com.kingzcheung.xime.settings.LanguagePreferences
import com.kingzcheung.xime.settings.LanguageSwitchPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reuse the settings activity; onNewIntent handles explicit destinations without recreating it. */
internal fun settingsActivityIntent(context: android.content.Context, route: String? = null) =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        route?.let { putExtra("open_fragment", it) }
    }

/**
 * 方案管理与输入模式切换。
 *
 * 承载方案切换（switchSchema/applyPageSizeSetting）、部署（reloadConfig/deploy/deploySchema/downloadSchema）、
 * 中英切换（switchInputMethod）、工具栏编辑动作、键盘高度与浮动模式调整。
 * 共享状态通过 service 引用访问。
 */
internal class ImeSchemaController(private val service: XimeInputMethodService) {
    private fun currentLanguage(): InputLanguage = if (service.rimeEngine.isAsciiMode()) InputLanguage.ENGLISH
        else InputModes.languageOf(service.rimeEngine.getCurrentSchema(), service.uiState.value.schemas)

    internal suspend fun switchInputMethod(targetLanguage: InputLanguage? = null,
        useLanguageKeyPreference: Boolean = false,
        languageSwitchMode: com.kingzcheung.xime.settings.LanguageSwitchMode? = null): Boolean {
        val fromLanguage = currentLanguage()
        val currentSchema = service.rimeEngine.getCurrentSchema()
        val nativeLanguage = InputModes.languageOf(currentSchema, service.uiState.value.schemas)
        val entries = InputModes.available(service.uiState.value.schemas)
        val enabled = LanguagePreferences.enabled(service)
        val available = (entries.filter { it.profile.mode == InputMode.KEYBOARD }.map { it.profile.language } +
            listOfNotNull(nativeLanguage.takeIf { currentSchema.isNotBlank() })).filter { it in enabled }.toSet()
        // Schema-options retain explicit ASCII behavior. The language key reads its preference;
        // physical shortcuts may override this one request without changing that preference.
        val target = targetLanguage ?: if (useLanguageKeyPreference)
            LanguageSwitchPreferences.target(fromLanguage, nativeLanguage, LanguageSwitchPreferences.forRequest(
                LanguageSwitchPreferences.read(service), languageSwitchMode),
                InputModes.languageOrder(service), available)
        else if (service.rimeEngine.isAsciiMode()) nativeLanguage else InputLanguage.ENGLISH
        if (target == null || target !in available) return false
        val targetSchema = if (target == InputLanguage.ENGLISH) currentSchema else {
            val selected = InputModes.selectedProfiles(service)[target]
                ?: InputModes.rememberedModes(service, entries)[target]
            InputProfileSelection.preferred(entries, target, selected, currentSchema)?.schemaId
                ?: currentSchema.takeIf { nativeLanguage == target }
                ?: return false
        }
        val changesSchema = targetSchema != currentSchema
        if (changesSchema && service.japaneseInputController.commit() && service.keyRouter.hasPendingCandidateCommit) return false
        val candState = service.candidateState.value
        val pendingEnglish = candState.pendingEnglishText
        FileLogger.i(XimeInputMethodService.TAG, "switchInputMethod: start, pendingEnglish='${if (pendingEnglish.isEmpty()) '-' else pendingEnglish}', isComposing=${candState.isComposing}, candidates=${candState.candidates.size}")
        if (pendingEnglish.isNotEmpty()) {
            // 英文直接上屏模式：编码字符已逐字落盘，切模式只需结束本轮输入（清状态），
            // 不可再 commitText 否则会重复输出整个词。
            withContext(Dispatchers.Main) {
                service.candidateState.value = service.candidateState.value.copy(
                    pendingEnglishText = "",
                    associationCandidates = emptyList()
                )
            }
        } else if (candState.isComposing) {
            if (candState.candidates.isNotEmpty()) {
                service.keyRouter.selectCandidateAsync(0)
            } else if (!changesSchema) {
                val input = candState.inputText
                if (input.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        service.commitText(input)
                    }
                    service.rimeEngine.clearComposition()
                }
            }
        }
        if (changesSchema) {
            // Selecting a first T9/native candidate may only consume a prefix. Preserve the
            // complete remaining preview before replacing its engine, using the normal checked
            // commit path; a rejected editor commit must leave the original language intact.
            if (service.keyRouter.hasPendingCandidateCommit) return false
            val partial = service.t9PartialSegments.joinToString("") { it.text }
            val remainder = service.rimeEngine.conversionPreview().ifEmpty { service.rimeEngine.getInput() }
            if (partial.isNotEmpty() || remainder.isNotEmpty()) {
                service.keyRouter.commitSelectedText(partial + remainder)
                if (service.keyRouter.hasPendingCandidateCommit) return false
            }
        }
        // All engine changes remain in the key-processing FIFO, including native-language pairs.
        if (fromLanguage != InputLanguage.ENGLISH) {
            InputModes.rememberMode(service, currentSchema, nativeLanguage)
        }
        if (changesSchema) {
            applyPageSizeSetting(targetSchema)
            if (!service.rimeEngine.switchSchema(targetSchema)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(service, "所选方案尚未就绪，请检查输入资源", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }
        val targetAscii = target == InputLanguage.ENGLISH
        if (service.rimeEngine.isAsciiMode() != targetAscii && !service.rimeEngine.toggleAsciiMode()) {
            FileLogger.e(XimeInputMethodService.TAG, "switchInputMethod: engine unavailable")
            withContext(Dispatchers.Main) { Toast.makeText(service, "输入法引擎不可用，请稍后再试", Toast.LENGTH_SHORT).show() }
            return false
        }
        if (!service.rimeEngine.isAsciiMode()) {
            // 回到中文/日文：按用户的「全角／半角」选择回写标点宽度。
            // 过去只恢复 full_shape（且取自 user.yaml），与菜单显示用的是两套状态，
            // 于是出现"显示半角、实际输出全角"。
            service.sessionController.applyPunctuationWidth(service.rimeEngine.isAsciiMode())
        }
        service.sessionController.persistSchemaOption("ascii_mode", service.rimeEngine.isAsciiMode())
        withContext(Dispatchers.Main) {
            // 显式同步 uiState.isAsciiMode（权威源 = rime 引擎状态），
            // 不依赖 updateUI 链路异步回写，避免键盘 UI 与 rime 状态脱钩。
            val ascii = service.rimeEngine.isAsciiMode()
            FileLogger.i(XimeInputMethodService.TAG, "switchInputMethod: rime ascii=$ascii, ui before=${service.uiState.value.isAsciiMode}")
            service.uiState.value = service.uiState.value.copy(isAsciiMode = ascii,
                currentSchemaId = service.rimeEngine.getCurrentSchema())
            if (changesSchema) {
                com.kingzcheung.xime.handwriting.HandwritingEngine.release()
                val model = service.keyboardViewModel
                model.discardTemporaryHandwriting()
                model.asciiStateMachine.reset()
                model.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
                // All pending text was accepted above. Reset only the old T9 display/selection
                // state here, never queue an engine clear after the new schema is installed.
                service.keyboardCallbacks?.onT9CompositionCleared?.invoke()
                service.t9PartialSegments.clear()
                service.uiState.value = service.uiState.value.copy(
                    t9RightCandidateSelectedCount = 0, t9SelectedCandidatePinyin = "")
                service.sessionController.updateSchemaName()
            }
            SettingsPreferences.setCurrentSchema(service, service.rimeEngine.getCurrentSchema(),
                com.kingzcheung.xime.settings.InputModes.languageOf(service.rimeEngine.getCurrentSchema(), service.uiState.value.schemas))
            service.updateUI()
            // 主线程直接权威下发键盘布局切换（与 rime 状态一致），
            // 不依赖 Compose LaunchedEffect 侦测 uiState 后再异步 dispatch（部分机型调度延迟导致 UI 不更新）。
            val schemaId = service.rimeEngine.getCurrentSchema()
            service.keyboardViewModel.dispatch(
                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(ascii, schemaId)
            )
        }
        if (changesSchema) service.japaneseInputController.cancel()
        return true
    }
    
    internal fun applyCustomLayouts() {
        service.keyRouter.postRimeJob {
            val prefs = SettingsPreferences.getPrefsPublic(service)
            try {
                KeysConfigHelper.loadConfig(service)
                check(RimeConfigHelper.ensureDeployment(service)) { "方案部署失败" }
                val id = SettingsPreferences.getCurrentSchema(service)
                check(service.rimeEngine.switchSchema(id)) { "无法恢复当前输入方案" }
                withContext(Dispatchers.Main) {
                    service.sessionController.updateSchemaName()
                    service.updateUI()
                    prefs.edit().putString(com.kingzcheung.xime.settings.CustomKeyboardLayouts.STATUS, "布局已应用，可从中文输入模式选择").apply()
                }
            } catch (e: Exception) {
                prefs.edit().putString(com.kingzcheung.xime.settings.CustomKeyboardLayouts.STATUS, "已保存，但应用失败：${e.message}；可在菜单重新部署").apply()
            }
        }
    }

    internal fun reloadConfig() {
        
        service.mainHandler.post {
            service.requestHideSelf(0)
            android.widget.Toast.makeText(service, "方案部署中...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 部署投递到 key-processing 队列：与按键/切换同队列串行执行，
        // 部署期间输入/切换操作排队等待，完成后自动恢复，
        // 不再因 rimeLock 被部署占用而失败或静默丢弃。
        service.keyRouter.postRimeJob {
            try {
                KeysConfigHelper.loadConfig(service)
                // 重新加载配色方案（用户可能在 xime.custom.yaml 中修改了 color_schemes）
                KeyboardThemes.reload(service)
                
                check(RimeConfigHelper.redeploy(service)) { "方案部署失败，输入会话未就绪" }

                // 部署完成后重新加载配置（Rime 可能在部署过程中改写文件）
                KeysConfigHelper.loadConfig(service)
                KeyboardThemes.reload(service)
                
                val availableSchemas = service.rimeEngine.getAvailableSchemas()
                
                val savedSchema = SettingsPreferences.getCurrentSchema(service)
                if (savedSchema in availableSchemas) {
                    applyPageSizeSetting(savedSchema)
                    service.rimeEngine.switchSchema(savedSchema)
                } else {
                    FileLogger.w(XimeInputMethodService.TAG, "Schema $savedSchema not found in available schemas")
                }
                
                // 直接在 key-processing 线程同步读取 name，避免嵌套协程的时序问题
                val currentSchemaId = service.rimeEngine.getCurrentSchema()
                val schemaName = SchemaManager.getSchemaDisplayName(
                    service, currentSchemaId
                ) ?: currentSchemaId

                withContext(Dispatchers.Main) {
                    service.uiState.value = service.uiState.value.copy(
                        schemaName = schemaName,
                        currentSchemaId = currentSchemaId,
                    )
                    service.updateUI()
                    android.widget.Toast.makeText(service, "方案部署完成", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                FileLogger.e(XimeInputMethodService.TAG, "Failed to reload config", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(service, "方案部署失败，请在输入方案设置中重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    internal fun openSettings(route: String? = null) {
        // The keyboard is already editing CyIME's own settings test field.
        // A generic Settings tap should not even relaunch the activity or disturb focus.
        if (route == null && service.currentInputEditorInfo?.packageName == service.packageName) return
        try {
            service.startActivity(settingsActivityIntent(service, route))
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to open settings", e)
        }
    }
    
    private val editorCursor = EditorCursor()
    private val japaneseKanaComposer = JapaneseKanaComposer()
    private var japaneseKanaSession = Long.MIN_VALUE

    internal fun resetEditorSelection() = editorCursor.resetSelection()

    internal fun handleJapaneseKanaAction(action: JapaneseKanaAction) {
        val original = service.currentInputConnection ?: return
        val inputSession = service.uiState.value.inputSessionId
        val profileId = service.uiState.value.currentSchemaId
        service.keyRouter.postRimeJob {
            if (service.currentInputConnection !== original || service.uiState.value.inputSessionId != inputSession ||
                service.rimeEngine.getCurrentSchema() != profileId || service.rimeEngine.isAsciiMode()) return@postRimeJob
            val profile = com.kingzcheung.xime.settings.InputProfiles.current(profileId)
            if (profile.scheme != com.kingzcheung.xime.settings.InputScheme.KANA ||
                profile.layout.kind != com.kingzcheung.xime.settings.LayoutKind.KANA_KEYPAD) return@postRimeJob
            if (japaneseKanaSession != inputSession) {
                japaneseKanaComposer.reset()
                japaneseKanaSession = inputSession
            }
            service.japaneseInputController.prepareKana(action == JapaneseKanaAction.Modify)
            val before = service.rimeEngine.getInput()
            var result: RimeProcessResult? = null
            val committed = StringBuilder()
            when (action) {
                is JapaneseKanaAction.Input -> {
                    if (action.romaji !in japaneseKanaRomaji) return@postRimeJob
                    var allProcessed = true
                    for (char in action.romaji) {
                        val next = service.rimeEngine.processQueuedKeyAndGetResult(char.code, 0)
                        result = next
                        committed.append(next.committedText)
                        if (!next.processed) { allProcessed = false; break }
                    }
                    if (allProcessed) japaneseKanaComposer.recordInput(before, action.romaji, result?.inputText.orEmpty(), committed.isNotEmpty())
                    else japaneseKanaComposer.reset()
                }
                JapaneseKanaAction.Modify -> {
                    if (japaneseKanaComposer.replacement(before) == null && before.isNotEmpty()) {
                        val boundary = service.rimeEngine.previousJapaneseBoundary(before)
                        japaneseKanaComposer.recordInput(before.take(boundary), before.drop(boundary), before, false)
                    }
                    val replacement = japaneseKanaComposer.replacement(before) ?: return@postRimeJob
                    // 整体改写引擎中的未提交编码，不发送退格，绝不删除宿主里已经上屏的字。
                    if (!service.rimeEngine.setInput(replacement.input)) return@postRimeJob
                    result = service.rimeEngine.getProcessResult(true)
                    committed.append(result.committedText)
                    if (committed.isEmpty()) japaneseKanaComposer.recordReplacement(replacement, result.inputText)
                    else japaneseKanaComposer.reset()
                }
            }
            val finalResult = result ?: return@postRimeJob
            val transformed = service.candidateTransform.transformFor(finalResult)
            withContext(Dispatchers.Main) {
                if (service.currentInputConnection !== original || service.uiState.value.inputSessionId != inputSession ||
                    service.uiState.value.currentSchemaId != profileId) return@withContext
                if (committed.isNotEmpty()) service.commitText(committed.toString())
                service.sessionController.updateUIWithResult(
                    transformed?.let { finalResult.copy(candidates = it.candidates.toTypedArray()) } ?: finalResult,
                    transformed?.actions ?: emptyList(),
                )
            }
        }
    }

    private fun editCursor(finishComposition: Boolean = true, action: (InputConnection) -> Unit) {
        val original = service.currentInputConnection ?: return
        val inputSession = service.uiState.value.inputSessionId
        service.keyRouter.postRimeJob {
            withContext(Dispatchers.Main) {
                // 焦点/会话检查与清理必须在同一个主线程任务内，中间不可挂起，
                // 否则队列里旧输入框的编辑操作可能清掉新输入框的组合串。
                if (service.currentInputConnection !== original || service.uiState.value.inputSessionId != inputSession) {
                    return@withContext
                }
                val preserveComposition = finishComposition &&
                    SettingsPreferences.getInputTextLocation(service) == SettingsPreferences.INPUT_TEXT_INPUT_BOX &&
                    service.candidateState.value.isComposing
                if (preserveComposition) {
                    service.finishComposingInputBoxKeepingText()
                    service.rimeEngine.clearComposition()
                }
                action(original)
                if (preserveComposition) service.updateUI()
            }
        }
    }

    internal fun moveEditorCursorVertical(steps: Int) = editCursor { editorCursor.moveVertical(it, steps) }

    internal fun moveEditorCursor(steps: Int, onStep: () -> Unit = {}) =
        editCursor { editorCursor.moveWithFeedback(it, steps, onStep) }

    /** 已在按键队列中，不能再次排队，否则长按方向键会积压到后续操作之后。 */
    internal suspend fun moveJapaneseCursorFromKeyQueue(steps: Int) {
        val original = service.currentInputConnection ?: return
        val owner = service.uiState.value.inputSessionId
        withContext(Dispatchers.Main) {
            if (service.currentInputConnection === original && service.uiState.value.inputSessionId == owner) {
                editorCursor.move(original, steps)
            }
        }
    }

    internal fun handleToolbarEditingAction(action: String) {
        val ic = service.currentInputConnection ?: return
        when (action) {
            "undo" -> service.performEditorMenuAction(android.R.id.undo)
            "redo" -> service.performEditorMenuAction(android.R.id.redo)
            "select_all" -> ic.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic.performContextMenuAction(android.R.id.copy)
            "cut" -> ic.performContextMenuAction(android.R.id.cut)
            "paste" -> {
                service.dismissPredictionsForPaste()
                ic.performContextMenuAction(android.R.id.paste)
            }
            "select_begin" -> editCursor { editorCursor.beginSelection(it) }
            "select_end" -> editCursor(finishComposition = false) { editorCursor.endSelection(it) }
            "select_reset" -> editorCursor.resetSelection()
            "home", "end", "select_paragraph_start", "select_paragraph_end" -> editCursor {
                editorCursor.moveToParagraphBoundary(it, toEnd = action.endsWith("end"), selecting = action.startsWith("select_"))
            }
            "arrow_left", "arrow_right", "select_arrow_left", "select_arrow_right" -> editCursor {
                editorCursor.move(it, if (action.endsWith("left")) -1 else 1, action.startsWith("select_"))
            }
            "arrow_up", "arrow_down", "select_arrow_up", "select_arrow_down" -> editCursor {
                editorCursor.sendDirection(it, if (action.endsWith("up")) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN,
                    action.startsWith("select_"))
            }
        }
    }

    internal fun applyPageSizeSetting(schemaId: String) {
        val userPageSize = SettingsPreferences.getPageSize(service)
        if (userPageSize > 0) {
            service.rimeEngine.setPageSize(schemaId, userPageSize)
        }
    }

    internal fun toggleHandwriting() {
        val model = service.keyboardViewModel
        if (model.exitTemporaryHandwriting()) {
            com.kingzcheung.xime.handwriting.HandwritingEngine.release()
            return
        }
        if (model.page.value == com.kingzcheung.xime.keyboard.KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)) {
            switchSchema(service.previousSchemaId.ifEmpty { service.rimeEngine.getCurrentSchema() }.ifEmpty { "pinyin_simp" })
            return
        }
        val language = service.uiState.value.inputProfile.language
        val recognitionLanguage = com.kingzcheung.xime.handwriting.HandwritingLanguages.recognitionLanguageForKeyboard(language)
        if (recognitionLanguage == null) {
            Toast.makeText(service, com.kingzcheung.xime.handwriting.HandwritingLanguages.unavailableMessage(language), Toast.LENGTH_SHORT).show()
            return
        }
        if (!com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(service, recognitionLanguage)) {
            Toast.makeText(service, "请先下载手写模型", Toast.LENGTH_SHORT).show()
            service.startActivity(android.content.Intent(service, com.kingzcheung.xime.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("open_fragment", "model_management")
            })
            return
        }
        model.enterTemporaryHandwriting()
    }

    internal fun switchSchema(schemaId: String) {
        if (schemaId == com.kingzcheung.xime.settings.InputModes.ENGLISH) {
            service.keyRouter.postRimeJob {
                if (!service.rimeEngine.isAsciiMode() && !switchInputMethod(targetLanguage = InputLanguage.ENGLISH)) return@postRimeJob
                val engineSchema = service.rimeEngine.getCurrentSchema()
                service.sessionController.persistSchemaOption("ascii_mode", true)
                withContext(Dispatchers.Main) {
                    com.kingzcheung.xime.handwriting.HandwritingEngine.release()
                    service.keyboardViewModel.discardTemporaryHandwriting()
                    service.keyboardViewModel.asciiStateMachine.reset()
                    service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
                    service.keyboardViewModel.dispatch(com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(true, engineSchema))
                    if (engineSchema.isNotEmpty()) SettingsPreferences.setCurrentSchema(service, engineSchema,
                        com.kingzcheung.xime.settings.InputModes.languageOf(engineSchema, service.uiState.value.schemas))
                    service.uiState.value = service.uiState.value.copy(isAsciiMode = true)
                    service.sessionController.updateSchemaName()
                }
            }
            return
        }
        if (com.kingzcheung.xime.settings.InputModes.languageOf(schemaId, service.uiState.value.schemas) !in
            com.kingzcheung.xime.settings.LanguagePreferences.enabled(service)) return
        if (isHandwritingSchema(schemaId)) {
            // 检查手写模型文件是否已下载
            if (!com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(service)) {
                FileLogger.w(XimeInputMethodService.TAG, "Handwriting model not found, redirecting to download")
                android.widget.Toast.makeText(
                    service, "请先下载手写模型", android.widget.Toast.LENGTH_LONG
                ).show()
                val intent = android.content.Intent(
                    service, com.kingzcheung.xime.MainActivity::class.java
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("open_fragment", "model_management")
                }
                service.startActivity(intent)
                return
            }
            service.keyboardViewModel.discardTemporaryHandwriting()
            service.previousSchemaId = service.rimeEngine.getCurrentSchema()
            SettingsPreferences.setCurrentSchema(service, schemaId,
                        com.kingzcheung.xime.settings.InputModes.languageOf(schemaId, service.uiState.value.schemas))
            service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
            // 手写模型按"用键盘时加载"管理：切到手写方案即展示手写键盘，
            // 由 HandwritingKeyboardLayout 创建时（LaunchedEffect）负责加载，
            // 此处不重复加载（避免与布局初始化并发双 bind/load）
            service.sessionController.updateSchemaName()
            return
        }
        // 显式选择语言方案与普通按键串行，成功后一次性更新引擎、页面和模式记忆。
        service.keyRouter.postRimeJob {
            try {
                val targetLanguage = InputModes.languageOf(schemaId, service.uiState.value.schemas)
                if (targetLanguage !in LanguagePreferences.enabled(service)) return@postRimeJob
                applyPageSizeSetting(schemaId)
                if (!service.rimeEngine.switchSchema(schemaId)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(service, if (service.rimeEngine.isMaintaining())
                            "词库部署中，请稍后再切换方案" else "方案未部署，请在方案管理中部署后再试", Toast.LENGTH_SHORT).show()
                    }
                    return@postRimeJob
                }
                // 英文是独立入口；选中中文/日语方案必须退出此前遗留的 ASCII 模式。
                service.rimeEngine.setOption("ascii_mode", false)
                service.sessionController.persistSchemaOption("ascii_mode", false)
                withContext(Dispatchers.Main) {
                    com.kingzcheung.xime.handwriting.HandwritingEngine.release()
                    val model = service.keyboardViewModel
                    model.discardTemporaryHandwriting()
                    model.asciiStateMachine.reset()
                    SettingsPreferences.setCurrentSchema(service, schemaId,
                        com.kingzcheung.xime.settings.InputModes.languageOf(schemaId, service.uiState.value.schemas))
                    service.uiState.value.schemas.firstOrNull { it.schemaId == schemaId }?.let {
                        com.kingzcheung.xime.settings.InputModes.selectProfile(service, it)
                    }
                    service.uiState.value = service.uiState.value.copy(isAsciiMode = false, currentSchemaId = schemaId)
                    model.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
                    model.dispatch(com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(false, schemaId))
                    service.sessionController.updateSchemaName()
                    service.updateUI()
                    Toast.makeText(service, "已切换输入方案", Toast.LENGTH_SHORT).show()
                }
                // 显示状态同步后再回写标点宽度：此处 uiState 可能还残留英文态，
                // 显式 asciiMode=false，保证中文/日文方案按用户选择输出标点。
                service.sessionController.applyPunctuationWidth(asciiMode = false)
            } catch (e: Exception) {
                FileLogger.e(XimeInputMethodService.TAG, "Failed to switch schema", e)
            }
        }
    }
    
    private fun downloadSchema(schemaId: String) {
        service.serviceScope.launch(Dispatchers.IO) {
            service.notifyDeploymentStatus(true, "正在下载 $schemaId...")
            
            val success = SchemaConfigHelper.downloadSchema(service, schemaId)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(service, "$schemaId 下载成功，请点击部署", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(service, "$schemaId 下载失败", Toast.LENGTH_SHORT).show()
                }
                service.notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun updateKeyboardHeightPreview(heightDp: Int) {
        service.keyboardContainer.updateHeight(heightDp)
    }
    
    internal fun setKeyboardHeight(heightDp: Int) {
        val isLandscape = service.resources.configuration.screenWidthDp > service.resources.configuration.screenHeightDp
        SettingsPreferences.setKeyboardHeightDp(service, heightDp, isLandscape)
        service.uiState.value = service.uiState.value.copy(keyboardHeightDp = heightDp)
        Toast.makeText(service, "键盘高度已调整", Toast.LENGTH_SHORT).show()
    }

    internal fun toggleFloatingMode(enabled: Boolean, navBarDp: Int = 0, persist: Boolean = true) {
        service.restoreKeyboardDisplayMode(enabled, navBarDp, persist)
    }

}
