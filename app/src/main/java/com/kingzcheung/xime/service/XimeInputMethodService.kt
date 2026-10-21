package com.kingzcheung.xime.service

import kotlinx.coroutines.flow.combine

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.util.Log
import com.kingzcheung.xime.settings.KeyboardHeightProfiles
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.kingzcheung.xime.ui.keyboard.LocalStretchFactor
import com.kingzcheung.xime.ui.keyboard.LocalClipboardPanelExpansion
import com.kingzcheung.xime.ui.keyboard.clipboardPanelBounds
import com.kingzcheung.xime.ui.keyboard.clipboardPanelCanExpand
import com.kingzcheung.xime.ui.keyboard.rememberClipboardPanelExpansion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.ui.keyboard.ProtectedKeyboardSize
import com.kingzcheung.xime.ui.keyboard.KeyboardDockTarget
import com.kingzcheung.xime.ui.keyboard.KeyboardAspectLimits
import com.kingzcheung.xime.ui.keyboard.letterKeyboardDefaults
import com.kingzcheung.xime.ui.keyboard.keyboardPhysicalMetrics
import com.kingzcheung.xime.ui.keyboard.protectKeyboardSize
import com.kingzcheung.xime.ui.keyboard.FLOATING_DRAG_BAR_HEIGHT_DP
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardCandidateBar
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardToolbar
import com.kingzcheung.xime.ui.keyboard.HardwareToolbarPosition
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.asCoroutineDispatcher
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.sync.ClipboardSyncBridge
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.api.ToolPlugin
import com.kingzcheung.xime.plugin.core.api.ToolResult
import com.kingzcheung.xime.plugin.core.lua.PluginEvent
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.rime.buildT9DisplayState
import com.kingzcheung.xime.rime.resolveRimeCandidateIndex

import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import com.kingzcheung.xime.ui.keyboard.isHandwritingSchema
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.keyboardBackground
import com.kingzcheung.xime.ui.theme.visualMaterial
import com.kingzcheung.xime.ui.keyboard.isBottomDocked
import com.kingzcheung.xime.ui.keyboard.HardwareCandidateRow
import com.kingzcheung.xime.ui.keyboard.CandidateBarVisuals
import kotlin.math.roundToInt
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.PreeditMergeHelper
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.keyboard.ActionExecutor
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.underlyingPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.plugin.core.api.PluginResultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Bundle
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import java.io.File
import java.io.FileInputStream

object QuickSendFormEditTextHolder {
    var editText: android.widget.EditText? = null
}

/** 快捷发送表单的触发编码输入框 holder（与内容输入框独立）。 */
object QuickSendFormCodeEditTextHolder {
    var editText: android.widget.EditText? = null
}

/** 通用工具面板输入框 holder（与快捷发送独立，避免互相覆盖）。 */
object ToolPanelEditTextHolder {
    var editText: android.widget.EditText? = null
}

/**
 * T9 九键 partial commit 段：右选部分提交时累积的（文本, 拼音）对。
 * 文本用于 preedit 拼接与上屏，拼音用于用户词典调频/回滚，两者同源同步维护。
 */
data class T9PartialSegment(val text: String, val pinyin: String)

class XimeInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, ActionExecutor {

    companion object {
        /** 键盘与语音悬浮条互换、悬浮条进出时内容下沉/上浮的距离。 */
        private const val CONTENT_SHIFT_DP = 24f
        internal const val TAG = "XimeInputMethodService"
        private const val DARK_MODE_LIGHT = 0
        private const val DARK_MODE_DARK = 1
        private const val DARK_MODE_SYSTEM = 2
        private const val HARDWARE_CANDIDATE_BAR_HEIGHT = 72
        internal const val SAFE_TEXT_LIMIT = 262144
        // 键盘收起超过这段时间才卸载联想模型；短暂切应用不重载
        private const val PREDICTION_MODEL_IDLE_RELEASE_MS = 3 * 60_000L

    }

    /** release 构建不输出调试日志，减少 logcat 写入开销。 */
    private fun debugLog(msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, msg)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    internal val rimeEngine = RimeEngine.getInstance()
    internal val inputReadiness = InputReadiness(android.os.SystemClock::uptimeMillis)
    
    internal lateinit var clipboardManager: ClipboardManager

    internal var clipboardSyncBridge: ClipboardSyncBridge? = null
    
    internal lateinit var keyboardContainer: VoiceKeyboardContainer
    
    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runtimeObserver: com.kingzcheung.xime.runtime.adaptation.RuntimeObserver? = null

    /** P1 observes proposals only; no feature subscribes to them as an execution policy yet. */
    private fun startRuntimeObservation() {
        if (runtimeObserver == null) {
            runtimeObserver = com.kingzcheung.xime.runtime.adaptation.RuntimeObserver(
                CoroutineScope(serviceScope.coroutineContext + Dispatchers.IO),
                com.kingzcheung.xime.runtime.adaptation.AndroidRuntimeSignalSource(this),
                { com.kingzcheung.xime.runtime.adaptation.RuntimePreferences.read(this) },
            ) { observation ->
                FileLogger.i("RuntimeObservation", "observation only: generation=${observation.generation}, memory=${observation.capabilities.memoryEstimate}, " +
                    "animation=${observation.proposal.animation.proposed}/${observation.proposal.animation.reasons}, " +
                    "keyGlow=${observation.proposal.keyGlow.proposed}/${observation.proposal.keyGlow.reasons}, " +
                    "neuralAdmission=${observation.proposal.neuralPrediction.proposed}/${observation.proposal.neuralPrediction.reasons}, " +
                    "correctionAdmission=${observation.proposal.voiceCorrection.proposed}/${observation.proposal.voiceCorrection.reasons}")
            }
        }
        runtimeObserver?.start()
    }
    @Volatile private var keyProcessingThread: Thread? = null
    private var inputStallWatchdog: InputStallWatchdog? = null
    internal val keyProcessingDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "key-process").also { it.isDaemon = true; keyProcessingThread = it }
    }.asCoroutineDispatcher()
    
    private val inputLatencyWindow = InputLatencyWindow()
    private val inputLatencyByKind = InputLatencyByKind()
    internal val inputCommands = com.kingzcheung.xime.rime.InputCommandQueue(serviceScope, keyProcessingDispatcher,
        reportLatency = { wait, execution ->
            if (wait >= 100 || execution >= 100) FileLogger.w("InputLatency", "queue_wait_ms=$wait action_ms=$execution")
            inputLatencyWindow.record(wait, execution)?.let { timing ->
                // The old 100 ms alarm misses a persistent extra display frame.
                // Report at most once per 64 actions, without logging what was typed.
                if (timing.totalP95Ms >= 16) FileLogger.w("InputLatencyWindow",
                    "actions=${timing.samples} total_median_ms=${timing.totalMedianMs} " +
                        "total_p95_ms=${timing.totalP95Ms} total_max_ms=${timing.totalMaxMs} " +
                        "queue_p95_ms=${timing.waitP95Ms} action_p95_ms=${timing.executionP95Ms}")
            }
        }, reportFailure = { failure ->
            FileLogger.e("InputLifecycle", "Input action failed; subsequent input remains active", failure)
        }, reportSample = { sample ->
            // 按动作类别分别给分位数，区分按键解码、选词、停顿重排与主线程等待；不记按键内容。
            inputLatencyByKind.record(sample)?.let { s ->
                FileLogger.w("InputLatencyKind", "kind=${s.kind} n=${s.samples} total_p50_ms=${s.totalP50} " +
                    "total_p95_ms=${s.totalP95} total_max_ms=${s.totalMax} queue_p95_ms=${s.waitP95} " +
                    "exec_p50_ms=${s.executionP50} exec_p95_ms=${s.executionP95} " +
                    "engine_p50_ms=${s.engineP50} engine_p95_ms=${s.engineP95} engine_max_ms=${s.engineMax}")
            }
        })
    internal val uiEventChannel = Channel<suspend () -> Unit>(Channel.CONFLATED)

    init {
        serviceScope.launch(Dispatchers.Main) {
            consumeInputUiUpdates(uiEventChannel) { failure ->
                FileLogger.e(TAG, "Candidate UI update failed; keeping subsequent updates active", failure)
            }
        }
    }
    
    internal val mainHandler = Handler(Looper.getMainLooper())
    private val releaseIdlePredictionModel = Runnable { AssociationManager.releaseModelWhileIdle() }
    
    internal val uiState = mutableStateOf(InputUIState())
    internal val candidateState = mutableStateOf(CandidateState())
    private val clipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private val voiceAmplitudeState = mutableFloatStateOf(0f)
    private val voiceSpectrumState = mutableStateOf(FloatArray(16))
    /** 实体键盘 Caps Lock 状态，按键事件到达即更新，供收起栏显示 ⇪。 */
    private val hardwareCapsLockState = mutableStateOf(false)
    private val quickSendItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    internal val recentClipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())


    private val bottomInsetPxState = mutableStateOf(0)
    // Connection changes must also recompose the full screen keyboard when compact stays false.
    private var hardwareKeyboardDisplay by mutableStateOf(HardwareKeyboardDisplayState())
    private val hasHardwareKeyboard: Boolean get() = hardwareKeyboardDisplay.connected
    private val hardwareInputManager by lazy { getSystemService(android.hardware.input.InputManager::class.java) }
    private val hardwareInputListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
        private fun refresh() {
            val previous = hasHardwareKeyboard
            updateHardwareKeyboardConnection()
            if (previous != hasHardwareKeyboard) {
                applyCompactMode()
                requestHardwareCursorUpdates()
            }
        }
    }

    private fun updateHardwareKeyboardConnection() {
        val devices = hardwareInputManager.inputDeviceIds.map(hardwareInputManager::getInputDevice).filterNotNull()
        hardwareKeyboardDisplay = hardwareKeyboardDisplay.withConnection(hasPhysicalTextKeyboard(devices))
    }
    private val hardwareToolbarPosition = mutableStateOf(HardwareToolbarPosition())
    private var hardwareViewportWidthDp = 0f
    private var hardwareViewportHeightDp = 0f
    internal fun hardwareAssociationLimit(): Int {
        if (!uiState.value.isCompact) return PredictionManager.MAX_ASSOCIATION_COUNT + 1
        return if (com.kingzcheung.xime.ui.keyboard.hardwareToolbarCanShowCandidates(
                hardwareViewportWidthDp, hardwareViewportHeightDp, hardwareToolbarPosition.value,
                uiState.value.hardwareOptions.dockAtEdge)) 10 else 0
    }
    private val hardwareToolbarBounds = mutableStateOf<android.graphics.Rect?>(null)
    private var hardwareCandidateBounds: android.graphics.Rect? = null
    private var hardwarePreeditBounds: android.graphics.Rect? = null
    private val glassBlurWindows = mutableMapOf<String, ImeGlassBlurWindow>()
    private val glassSurfaceBounds = mutableMapOf<String, android.graphics.Rect>()
    internal val editorInputPolicy = EditorSessionInputPolicy()
    private var glassConfig = com.kingzcheung.xime.settings.FrostedGlassConfig()
    private var glassWindowVisible = false
    private var glassSurfaceMode = ""
    // 整块键盘透明度与玻璃材质相乘：模糊窗口随按键一起淡出，而不是始终满强度地糊住宿主。
    private var glassOpacity = 1f

    /** Blur windows follow measured content bounds; they never determine keyboard geometry. */
    private fun updateGlassSurface(name: String, bounds: android.graphics.Rect?) {
        if (bounds == null || bounds.isEmpty) glassSurfaceBounds.remove(name)
        else glassSurfaceBounds[name] = android.graphics.Rect(bounds)
        val surface = glassBlurWindows[name] ?: if (glassConfig.enabled && bounds != null) {
            ImeGlassBlurWindow(this, name) { window.window?.decorView }.also { glassBlurWindows[name] = it }
        } else return
        val density = resources.displayMetrics.density
        surface.configure(glassConfig.enabled, (glassConfig.blurRadiusDp * density).roundToInt(),
            if (name == "navigation") 0f else 16f * density)
        surface.setOpacity(glassOpacity)
        surface.setRegion(glassSurfaceBounds[name])
        surface.setVisible(glassWindowVisible)
    }

    private fun onSplitGapChanged() {
        updateKeyboardGlass()
        if (::keyboardContainer.isInitialized) keyboardContainer.requestLayout()
    }

    /** 全屏手写时只模糊底部控件区，书写区保持清晰、能看见后面的应用。 */
    private var handwritingGlassControls: android.graphics.Rect? = null
    private val handwritingGlassRegionReporter: (android.graphics.Rect?) -> Unit = { bounds ->
        if (handwritingGlassControls != bounds) {
            handwritingGlassControls = bounds
            updateKeyboardGlass()
        }
    }

    /** 分体时模糊分三块：上方工具栏整宽，左右两半各一块，中缝保持清晰。 */
    private fun updateKeyboardGlass() {
        val card = if (uiState.value.handwritingExpanded) handwritingGlassControls else keyboardGlassCard
        val hole = card?.let { splitKeyboardGap.hole(it.bottom.toFloat()) }
        if (card == null || hole == null) {
            updateGlassSurface("keyboard", card)
            updateGlassSurface("keyboard-left", null)
            updateGlassSurface("keyboard-right", null)
            return
        }
        val top = hole.top.roundToInt().coerceIn(card.top, card.bottom)
        // 两半向上压住工具栏那块的圆角，拼接处不露缺口。
        val halfTop = (top - (16f * resources.displayMetrics.density).roundToInt()).coerceAtLeast(card.top)
        updateGlassSurface("keyboard", android.graphics.Rect(card.left, card.top, card.right, top))
        updateGlassSurface("keyboard-left", android.graphics.Rect(card.left, halfTop, hole.left.roundToInt(), card.bottom))
        updateGlassSurface("keyboard-right", android.graphics.Rect(hole.right.roundToInt(), halfTop, card.right, card.bottom))
    }

    /** 当前布局真正以分体显示（偏好开启且是 26 键字母布局）。 */
    private fun splitKeyboardActive(state: InputUIState): Boolean =
        SettingsPreferences.isSplitKeyboardEnabled(this) &&
            com.kingzcheung.xime.ui.keyboard.supportsSplitKeyboard(state.currentSchemaId, state.isAsciiMode)

    private fun updateGlassOpacity(opacity: Float) {
        if (glassOpacity == opacity) return
        glassOpacity = opacity
        glassBlurWindows.values.forEach { it.setOpacity(opacity) }
    }

    private fun updateGlassConfiguration(config: com.kingzcheung.xime.settings.FrostedGlassConfig, mode: String) {
        if (glassSurfaceMode != mode) {
            glassSurfaceMode = mode
            glassSurfaceBounds.clear()
            glassBlurWindows.values.forEach { it.setRegion(null) }
        }
        glassConfig = config.normalized()
        (glassSurfaceBounds.keys + glassBlurWindows.keys).toSet().forEach { name ->
            updateGlassSurface(name, glassSurfaceBounds[name])
        }
    }
    /** 当前输入框是否受限（密码/终端/NO_SUGGESTIONS，见 EditorInfoClassifier）。
     *  主线程写（onStartInput）、key-processing 线程读（英文联想短路），volatile 保证可见性。 */
    @Volatile
    private var editorRestricted: Boolean = false
    /** 秘密输入框（密码/TYPE_NULL）：英文联想与回删替换的统一禁用线 */
    private var editorSecret: Boolean = false
    private var floatingWinX = 100
    private var floatingWinY = 300
    
    internal var isTrackingVoiceButtons = false
    internal var voiceRecordingStarted = false
    private var pendingVoiceAction: (() -> Unit)? = null
    internal var composeViewRef: View? = null
    internal var lastClearedText: String = ""
    /** 累积的 partial commit 段列表（多段选词场景下逐段追加，文本+拼音同源，供调频/回滚） */
    internal val t9PartialSegments = mutableListOf<T9PartialSegment>()
    /** 键盘回调引用，用于在 RIME selectCandidate 前同步通知 T9 控制器 */
    internal var keyboardCallbacks: KeyboardCallbacks? = null
    private var keyboardReportsChinese = true
    internal var isChineseMode: Boolean
        get() = keyboardReportsChinese && !uiState.value.isAsciiMode &&
            uiState.value.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.CHINESE
        set(value) { keyboardReportsChinese = value }
    internal var currentEffectiveKeyboardHeight: Int = 0
    private var floatingCardBounds: android.graphics.Rect? = null
    private var fixedCardBounds: android.graphics.Rect? = null
    private var keyboardGlassCard: android.graphics.Rect? = null
    /** 分体中缝（窗口坐标）：触摸区让给宿主，玻璃模糊也避开它。 */
    internal val splitKeyboardGap = com.kingzcheung.xime.ui.keyboard.SplitKeyboardGap().also {
        it.onChanged = { onSplitGapChanged() }
    }
    internal var currentFloatingCardWidthDp = 0
    internal var currentFloatingCardHeightDp: Int = 0
    /** Base keyboard only: expanded clipboard/tool panels must never seed a resize. */
    internal var currentResolvedKeyboardSize: ProtectedKeyboardSize? = null
    internal var previousSchemaId: String = ""
    
    internal val calculatorEngine = com.kingzcheung.xime.calculator.CalculatorEngine()

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    internal val keyboardViewModel: KeyboardViewModel by lazy {
        ViewModelProvider(
            _viewModelStore,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(applicationContext as android.app.Application)
        ).get(KeyboardViewModel::class.java)
    }

    /** 候选展开页自动收起：候选与联想均空（编码删空）时收起在位展开页，不留空页。
     *  在状态生产端调用（而非依赖 UI 重组观察）——候选变化时键盘区作用域被设计为跳过重组。 */
    internal fun maybeCollapseCandidatePage() {
        if (!keyboardViewModel.candidatePageExpanded.value) return
        val cs = candidateState.value
        if (cs.candidates.isEmpty() && cs.associationCandidates.isEmpty()) {
            keyboardViewModel.setCandidatePageExpanded(false)
        }
    }

    private val expandedCandidateLoader by lazy {
        ExpandedCandidateLoader(serviceScope, keyProcessingDispatcher, rimeEngine::readCandidateBatch)
    }

    /** Expanded candidates belong to an engine revision and editor session, never display text. */
    internal fun refreshExpandedCandidates() {
        expandedCandidateLoader.cancel()
        candidateState.value = candidateState.value.copy(expandedCandidates = emptyList(), expandedCandidatesLoaded = false)
        if (!keyboardViewModel.candidatePageExpanded.value) return
        val snapshot = candidateState.value
        val session = uiState.value.inputSessionId
        val admission = inputReadiness.ticket() ?: return
        japaneseInputController.displayCandidates(snapshot.inputText)?.let {
            candidateState.value = snapshot.copy(expandedCandidates = it, expandedCandidatesLoaded = true)
            return
        }
        expandedCandidateLoader.load(snapshot.engineRevision, isCurrent = {
            inputReadiness.accepts(admission) && uiState.value.inputSessionId == session &&
                keyboardViewModel.candidatePageExpanded.value &&
                candidateState.value.engineRevision == snapshot.engineRevision
        }) { candidates ->
            candidateState.value = candidateState.value.copy(expandedCandidates = candidates, expandedCandidatesLoaded = true)
        }
    }

    /** 硬件键盘在展开态按 DPAD_DOWN/UP：展开页滚动一屏（经事件流驱动 UI） */
    internal fun expandedPageScroll(direction: Int) {
        keyboardViewModel.requestExpandedPageScroll(direction)
    }
    
    internal val predictionManager: PredictionManager = PredictionManager(
        context = this,
        serviceScope = serviceScope,
        onPredictionResult = { candidates ->
            candidateState.value = candidateState.value.copy(
                associationCandidates = if (isChineseMode && !candidateState.value.isComposing &&
                    candidateState.value.inputText.isEmpty() && canPredictAfter(predictionManager.lastCommittedText)) candidates else emptyList()
            )
        },
    )
    
    internal val voiceRecognitionHandler = VoiceRecognitionHandler(
        context = this,
        onStateChanged = { newState -> uiState.value = newState },
        getState = { uiState.value },
        getInputConnection = { currentInputConnection },
        onRecordingStopped = { restoreAfterVoiceFinish() },
        onVoiceComplete = {
            val action = pendingVoiceAction
            pendingVoiceAction = null
            action?.invoke()

            restoreAfterVoiceFinish()
        },
        onAmplitudeChanged = { amplitude ->
            voiceAmplitudeState.floatValue = amplitude
        },
        onSpectrumChanged = { spectrum ->
            voiceSpectrumState.value = spectrum
        },
        onComposingWritten = { markInputBoxComposing() }
    )

    /**
     * 结束语音会话的统一出口：停止预启动并进入收尾——等待引擎最终结果后提交，
     * 超时回退提交部分结果（见 VoiceRecognitionHandler.finishRecognition）。
     * 键盘状态在收尾完成时经 onVoiceComplete → [restoreAfterVoiceFinish] 恢复，
     * 期间语音面板保持"正在识别..."显示，避免用户感知到结果被截断。
     * 幂等：收尾已在进行中时重复调用自动跳过。
     */
    internal fun endVoiceSession() {
        voiceRecognitionHandler.cancelPreStart()
        voiceRecognitionHandler.finishRecognition()
    }

    /** 语音会话真正完成（最终结果已提交/超时兜底/出错）后恢复键盘状态。幂等。 */
    internal fun restoreAfterVoiceFinish() {
        if (uiState.value.isVoiceMode && uiState.value.voiceSticky) feedbackManager.playKeySound("voice_end")
        // 兜底：会话结束的任何路径都确保录音已请求停止（幂等，正常松手路径
        // recordingThread 已置空时直接返回）。UI 状态一旦清零，松手停止链就失效，
        // 这里漏一次 stop 麦克风/引擎连接就会一直后台占用。
        voiceRecognitionHandler.stopRecognition()
        keyboardViewModel.exitVoice()
        isTrackingVoiceButtons = false
        voiceRecordingStarted = false
        voiceAmplitudeState.floatValue = 0f
        uiState.value = uiState.value.copy(
            isVoiceMode = false,
            voiceSticky = false,
            voiceButtonState = VoiceButtonState(),
            voiceRecognitionState = RecognitionState.IDLE,
            voiceRecognizedText = "",
            voiceAmplitude = 0f
        )
    }
    
    private var sharedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var clipboardCollectorJob: kotlinx.coroutines.Job? = null
    
    internal val feedbackManager = FeedbackManager(this)

    internal val keyRouter = ImeKeyRouter(this)

    internal val sessionController = ImeSessionController(this)

    internal val schemaController = ImeSchemaController(this)
    internal val japaneseInputController = JapaneseInputController(this)

    internal val textCommit = ImeTextCommit(this)
    
    private val inlineSuggestionManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        InlineSuggestionManager(this)
    } else null
    
    private fun loadDarkModePreference() {
        currentResolvedKeyboardSize = null
        if (uiState.value.showKeyboardResize) {
            // 编辑事务的几何不从偏好回灌；确认写完后统一恢复普通读取。
            uiState.value = uiState.value.copy(
                darkMode = SettingsPreferences.getEffectiveDarkMode(this),
                themeId = SettingsPreferences.getKeyboardTheme(this),
                toolbarButtons = SettingsPreferences.getToolbarButtons(this),
                menuButtons = SettingsPreferences.getMenuButtons(this),
                isSttEnabled = SettingsPreferences.isSttEnabled(this),
            )
            return
        }
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        val isFloatingMode = if (uiState.value.showKeyboardResize) uiState.value.isFloatingMode
            else SettingsPreferences.isFloatingMode(this, isLandscape)
        val loadedX = SettingsPreferences.getFloatingOffsetX(this, isLandscape)
        val loadedY = SettingsPreferences.getFloatingOffsetY(this, isLandscape)
        val screenW = resources.configuration.screenWidthDp
        val screenH = resources.configuration.screenHeightDp
        // 悬浮窗口的坐标宿主使用物理可见高度，而不是 configuration.screenHeightDp。
        // 后者在 IME 已显示时可能已经被宿主窗口/系统栏裁过，再拿它限制悬浮卡片会导致顶部直接被截掉。
        val physicalScreenDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt()
        val statusBarDp = tryGetStatusBarHeightDp(this, window.window)
        val effectiveH = if (isFloatingMode) (physicalScreenDp - statusBarDp).coerceAtLeast(1) else screenH

        val savedWidth = SettingsPreferences.getFloatingWidthDp(this, isLandscape)
        val bottomPadding = SettingsPreferences.getKeyboardBottomPaddingDp(this)
        val fixedWidth = SettingsPreferences.getFixedWidthDp(this, isLandscape)
        val fixedOffset = SettingsPreferences.getFixedOffsetX(this, isLandscape)
        KeyboardHeightProfiles.migrateLegacy(this, isLandscape, effectiveH)
        // 新版第一次启动时只做一次：把 1.14.26–1.14.27 规则留下的越界/偏高固定高度改回 1.14.25 的默认值。
        KeyboardHeightProfiles.normalizeFixedHeightsOnce(this)
        val kbH = KeyboardHeightProfiles.selected(this, isFloatingMode, isLandscape, effectiveH)
        val minY = if (isFloatingMode) maxOf(
            tryGetVisibleNavBarHeightDp(this, window.window),
            kotlin.math.ceil(bottomInsetPxState.value / resources.displayMetrics.density).toInt(),
        ) else 0
        // Position limits must use the same automatic/manual size as rendering.
        // Old percentage heights incorrectly moved otherwise valid floating positions on reload.
        val displaySize = resolveKeyboardSize(screenW, effectiveH, minY, uiState.value.copy(
            isFloatingMode = isFloatingMode, floatingWidthDp = savedWidth,
            fixedWidthDp = fixedWidth, keyboardBottomPaddingDp = bottomPadding,
        ))
        val halfMargin = maxOf(0, (screenW - displaySize.width) / 2)
        val cardH = displaySize.height + (if (isFloatingMode) FLOATING_DRAG_BAR_HEIGHT_DP else 0)
        val maxY = maxOf(minY, effectiveH - cardH)
        val clampedX = loadedX.coerceIn(-halfMargin, halfMargin)
        val clampedY = loadedY.coerceIn(minY, maxY)
        // 越界只在当前视口中夹紧，不让一次配置/主题刷新改写另一个方向的保存位置。
        uiState.value = uiState.value.copy(
            darkMode = SettingsPreferences.getEffectiveDarkMode(this),
            themeId = SettingsPreferences.getKeyboardTheme(this),
            isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService),
            keyboardHeightDp = kbH,
            keyboardBottomPaddingDp = bottomPadding,
            keyboardOpacity = SettingsPreferences.getKeyboardOpacity(this),
            toolbarButtons = SettingsPreferences.getToolbarButtons(this),
            menuButtons = SettingsPreferences.getMenuButtons(this),
            isFloatingMode = isFloatingMode,
            floatingOffsetX = clampedX,
            floatingOffsetY = clampedY,
            floatingWidthDp = savedWidth,
            fixedWidthDp = fixedWidth,
            fixedOffsetX = fixedOffset,
        )
    }
    
    private fun registerSharedPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(this)
        com.kingzcheung.xime.settings.IncognitoState.manual = SettingsPreferences.isIncognito(this)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith(com.kingzcheung.xime.settings.HardwareKeyboardPreferences.PREFIX) == true) {
                uiState.value = uiState.value.copy(hardwareOptions = com.kingzcheung.xime.settings.HardwareKeyboardPreferences.read(this))
            }
            when (key) {
                RimeConfigHelper.DEPLOYMENT_REVISION, com.kingzcheung.xime.settings.LanguagePreferences.KEY -> {
                    val pending = rimeInitializationJob
                    if (pending?.isActive == true) {
                        pending.invokeOnCompletion { serviceScope.launch { initRimeEngine() } }
                    } else initRimeEngine()
                }
                com.kingzcheung.xime.settings.InputModes.ORDER_KEY -> {
                    uiState.value = uiState.value.copy(schemas =
                        com.kingzcheung.xime.settings.InputModes.ordered(this@XimeInputMethodService, uiState.value.schemas))
                }
                com.kingzcheung.xime.settings.CustomKeyboardLayouts.REVISION -> schemaController.applyCustomLayouts()
                SettingsPreferences.KEY_INCOGNITO -> {
                    com.kingzcheung.xime.settings.IncognitoState.manual = SettingsPreferences.isIncognito(this)
                    applyIncognito()
                }
                SettingsPreferences.KEY_VISUAL_STYLE, SettingsPreferences.KEY_ROUNDED_KEYBOARD_BOTTOM, "dark_mode", "keyboard_theme", "show_bottom_buttons", "keyboard_height_dp", "keyboard_height_dp_landscape", "keyboard_bottom_padding_dp", "keyboard_opacity" -> {
                    com.kingzcheung.xime.ui.theme.VisualStyles.current = SettingsPreferences.getVisualStyle(this@XimeInputMethodService)
                    loadDarkModePreference()
                    applyWindowBackground()
                }
                "floating_mode", "floating_mode_landscape", "floating_width_dp", "floating_width_dp_landscape",
                KeyboardHeightProfiles.FLOATING_HEIGHT, KeyboardHeightProfiles.FLOATING_HEIGHT_LANDSCAPE -> {
                    loadDarkModePreference()
                    applyWindowBackground()
                }
                "stt_enabled" -> {
                    uiState.value = uiState.value.copy(isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService))
                }
                SettingsPreferences.KEY_SMART_PREDICTION_ENABLED -> onPredictionSettingChanged()
                SettingsPreferences.KEY_CLIPBOARD_SYNC_ENABLED -> updateClipboardSync()
                SettingsPreferences.KEY_CLIPBOARD_SYNC_PLUGIN_ID -> {
                    stopClipboardSync()
                    updateClipboardSync()
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    /** 设置驱动：智能联想开启时加载联想模型，关闭时卸载。 */
    private fun onPredictionSettingChanged() {
        val enabled = SettingsPreferences.isSmartPredictionEnabled(this)
        if (enabled) {
            serviceScope.launch(Dispatchers.IO) {
                com.kingzcheung.xime.association.AssociationManager.initialize(this@XimeInputMethodService)
            }
        } else {
            com.kingzcheung.xime.association.AssociationManager.release()
        }
    }
    
    private fun saveDarkModePreference(mode: Int) {
        SettingsPreferences.setDarkMode(this, mode)
        uiState.value = uiState.value.copy(darkMode = mode)
    }
    
    fun toggleDarkMode() {
        val currentMode = uiState.value.darkMode
        val newMode = when (currentMode) {
            DARK_MODE_LIGHT -> DARK_MODE_DARK
            DARK_MODE_DARK -> DARK_MODE_LIGHT
            else -> { // DARK_MODE_SYSTEM: 切换到当前系统主题的反面
                if (isDarkTheme()) DARK_MODE_LIGHT else DARK_MODE_DARK
            }
        }
        saveDarkModePreference(newMode)
    }
    
    fun isDarkTheme(): Boolean {
        com.kingzcheung.xime.ui.theme.VisualStyles.current.dark?.let { return it }
        return when (uiState.value.darkMode) {
            DARK_MODE_DARK -> true
            DARK_MODE_SYSTEM -> {
                val nightModeFlags = resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.kingzcheung.xime.ui.keyboard.HandwritingGlassRegion.reporter = handwritingGlassRegionReporter
        // 记录与打字速度有关的本机状态（用户词库体积、可选模型、内存余量），只记数量不记内容。
        serviceScope.launch(Dispatchers.IO) {
            delay(5_000)
            runCatching { FileLogger.w("InputInventory", InputInventory.describe(this@XimeInputMethodService)) }
        }
        // 允许 IME 窗口绘制到摄像头挖孔/刘海区域（横屏时背景覆盖全屏）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.window?.attributes?.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        savedStateRegistryController.performRestore(null)
        window.window?.decorView?.setViewTreeLifecycleOwner(this)
        window.window?.decorView?.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        
        FileLogger.init(this)
        FileLogger.i(TAG, "XimeInputMethodService created")
        hardwareInputManager.registerInputDeviceListener(hardwareInputListener, mainHandler)
        FileLogger.i(
            TAG,
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), " +
                "screen=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}@${resources.displayMetrics.density}"
        )
        
        feedbackManager.initialize()
        
        loadDarkModePreference()
        registerSharedPrefsListener()
        
        initRimeEngine()
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                initClipboardManager()
                initAssociationEngine()
                initSpeechRecognition()

                withContext(Dispatchers.Main) {
                    FileLogger.i(TAG, "Service initialization completed")
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Initialization failed: ${e.message}")
            }
        }
    }
    
    private fun initSpeechRecognition() {
        voiceRecognitionHandler.initialize()
        serviceScope.launch {
            androidx.compose.runtime.snapshotFlow { uiState.value.voiceKeyboardLanguages }.collect {
                voiceRecognitionHandler.onInputLanguageChanged()
            }
        }
    }
    
    private fun initAssociationEngine() {
        predictionManager.initialize()
    }
    
    
    private fun getPredictionFromPlugin(contextText: String) {
        if (!isChineseMode || !canPredictAfter(contextText)) return
        predictionManager.getPrediction(contextText)
    }
    
    private var rimeInitializationJob: kotlinx.coroutines.Job? = null

    private fun initRimeEngine() {
        if (rimeInitializationJob?.isActive == true) return
        inputReadiness.deployment(true)
        Log.d(TAG, "initRimeEngine: Starting initialization...")
        
        // Parsing all layout YAMLs can take seconds on first launch. Keep the service
        // main thread responsive; the existing loading overlay gates input until the
        // configVersion publication and engine initialization have both completed.
        uiState.value = uiState.value.copy(isDeploying = true, deploymentMessage = "正在加载键盘配置...")
        
        rimeInitializationJob = serviceScope.launch(Dispatchers.IO) {
            try {
                KeysConfigHelper.loadConfig(this@XimeInputMethodService)

                RimeEngine.setDeploymentCallback { isDeploying, message ->
                    // A native "finished" callback only means nativeInitialize returned.
                    // Deployment and schema restoration still have to finish before typing.
                    if (isDeploying) notifyDeploymentStatus(true, message)
                }

                notifyDeploymentStatus(true, "正在准备词库，首次使用需要稍候...")
                RimeConfigHelper.prepareAutomatically(this@XimeInputMethodService) { notifyDeploymentStatus(true, it) }
                check(rimeEngine.ensureSession(180_000L)) { "输入会话未就绪" }

                withContext(Dispatchers.Main) {
                    val availableSchemas = rimeEngine.getAvailableSchemas()
                    val storedSchema = SettingsPreferences.getCurrentSchema(this@XimeInputMethodService)
                    val selectableSchemas = if (storedSchema.isNotBlank() && storedSchema !in availableSchemas && storedSchema != com.kingzcheung.xime.settings.InputModes.ENGLISH &&
                        (isHandwritingSchema(storedSchema) || SchemaManager.isSchemaCompiled(this@XimeInputMethodService, storedSchema)))
                        availableSchemas.toList() + storedSchema else availableSchemas.toList()
                    val savedSchema = com.kingzcheung.xime.settings.LanguagePreferences.nativeSchema(
                        storedSchema, selectableSchemas,
                        com.kingzcheung.xime.settings.LanguagePreferences.enabled(this@XimeInputMethodService))
                    if (savedSchema != storedSchema) SettingsPreferences.setCurrentSchema(this@XimeInputMethodService, savedSchema)
                    val currentSchema = rimeEngine.getCurrentSchema()
                    Log.d(TAG, "initRimeEngine: currentSchema=$currentSchema, savedSchema=$savedSchema, availableSchemas=${availableSchemas.joinToString()}")
                    
                    when {
                        isHandwritingSchema(savedSchema) -> {
                            // 手写方案：不要调 rimeEngine.switchSchema（Rime 没有手写引擎），
                            // 也不要覆盖 savedSchema（由 onStartInput 恢复 UI）
                            Log.d(TAG, "initRimeEngine: savedSchema is handwriting, keeping current Rime schema")
                            // UI 布局恢复：冷启动时第一次 onStartInput 先于引擎初始化完成，
                            // RimeEngine.isInitialized()=false 会跳过 handwriting UI 恢复，
                            // 此处必须补切，否则第一次弹出键盘停留在默认全键盘
                            val hwDir = com.kingzcheung.xime.model.ModelStorage.getModelDir(
                                this@XimeInputMethodService, "ochwpro"
                            )
                            com.kingzcheung.xime.model.ModelStorage.migrateLegacyForModel(
                                this@XimeInputMethodService, "ochwpro"
                            )
                            val modelOk = java.io.File(hwDir, "ochwpro.onnx").exists() &&
                                java.io.File(hwDir, "char_index.json").exists()
                            if (modelOk) {
                                val page = keyboardViewModel.page.value
                                val alreadyHandwriting = page is com.kingzcheung.xime.keyboard.KeyboardPage.Main &&
                                    page.type == com.kingzcheung.xime.keyboard.MainType.HANDWRITING
                                if (!alreadyHandwriting) {
                                    keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
                                }
                                // 手写模型按"用键盘时加载"管理：不在此预载，
                                // HandwritingKeyboardLayout 创建时（LaunchedEffect）负责加载
                            } else {
                                FileLogger.w(TAG, "initRimeEngine: handwriting model missing, keep full keyboard")
                                android.widget.Toast.makeText(
                                    this@XimeInputMethodService,
                                    "请先下载手写模型",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        savedSchema in availableSchemas -> {
                            // 即使 savedSchema == currentSchema 也要调用 switchSchema，
                            // 因为 nativeCreateSession 后 schema 的 processor/translator 等
                            // 可能未完全初始化，switchSchema 会触发完整的初始化流程
                            Log.d(TAG, "initRimeEngine: Switching to saved schema: $savedSchema")
                            withContext(Dispatchers.IO) {
                                schemaController.applyPageSizeSetting(savedSchema)
                                check(rimeEngine.switchSchema(savedSchema)) { "输入方案加载失败" }
                            }
                        }
                        SchemaManager.isSchemaCompiled(this@XimeInputMethodService, savedSchema) -> {
                            Log.d(TAG, "initRimeEngine: Schema compiled but not in get_schema_list, switching anyway")
                            withContext(Dispatchers.IO) {
                                schemaController.applyPageSizeSetting(savedSchema)
                                check(rimeEngine.switchSchema(savedSchema)) { "输入方案加载失败" }
                            }
                        }
                        availableSchemas.isNotEmpty() -> {
                            // savedSchema 不可用且未编译，退而求其次用第一个可用方案
                            val fallbackSchema = availableSchemas.first()
                            Log.d(TAG, "initRimeEngine: savedSchema '$savedSchema' not available, falling back to '$fallbackSchema'")
                            withContext(Dispatchers.IO) {
                                schemaController.applyPageSizeSetting(fallbackSchema)
                                check(rimeEngine.switchSchema(fallbackSchema)) { "输入方案加载失败" }
                            }
                            SettingsPreferences.setCurrentSchema(this@XimeInputMethodService, fallbackSchema)
                        }
                    }
                    
                    sessionController.updateSchemaName()
                    // onStartInput 在部署进行中会跳过 schema 切换与选项恢复，
                    // 部署完成后这里补齐 UI 状态，保证键盘可用
                    withContext(Dispatchers.IO) { sessionController.restorePersistedSchemaOptions() }
                    val ascii = editorInputPolicy.engineAsciiOverride() ?: rimeEngine.isAsciiMode()
                    uiState.value = uiState.value.copy(isAsciiMode = ascii)
                    keyboardViewModel.dispatch(com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(
                        ascii, rimeEngine.getCurrentSchema()))
                    updateUI()
                    inputReadiness.completeStartup()
                    uiState.value = uiState.value.copy(isDeploying = false, deploymentMessage = "")
                    // Focus may have changed while native initialization was suspended.
                    // Queue the current field's preparation before accepting its first key.
                    currentInputEditorInfo?.let {
                        EditorInputPreparation(this@XimeInputMethodService).schedule(false,
                            EditorInfoClassifier.isNumberEditor(it))
                    }
                    Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                FileLogger.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
                notifyDeploymentStatus(true, "输入准备未完成，3 秒后自动重试")
                delay(3_000)
                withContext(Dispatchers.Main) { rimeInitializationJob = null; initRimeEngine() }
            }
        }
        
        // Watchdog reports timeout without permitting input against an unready engine.
        // withTimeout cannot cancel native JNI calls; if rimeEngine.initialize() hangs
        // in librime, the IO coroutine would block forever. This watchdog ensures the
        // user gets an explicit error; keyboard dismissal remains available.
        // 首次编译最多等 120s + ensureSession 60s + 10s 缓冲
        val attempt = rimeInitializationJob
        serviceScope.launch(Dispatchers.Main) {
            delay(190_000L)
            if (rimeInitializationJob === attempt && attempt?.isActive == true && uiState.value.isDeploying) {
                FileLogger.w(TAG, "initRimeEngine: Watchdog triggered - native init appears stuck, keeping input blocked")
                uiState.value = uiState.value.copy(
                    isDeploying = true,
                    deploymentMessage = "准备时间较长，仍在后台处理中，可先切换输入法"
                )
            }
        }
    }
    
    internal fun notifyDeploymentStatus(isDeploying: Boolean, message: String) {
        inputReadiness.deployment(isDeploying)
        serviceScope.launch(Dispatchers.Main) {
            uiState.value = uiState.value.copy(
                isDeploying = inputReadiness.ticket() == null,
                deploymentMessage = message
            )
        }
    }
    
    private fun initClipboardManager() {
        Log.d(TAG, "initClipboardManager: Starting initialization...")
        try {
            clipboardManager = ClipboardManager.getInstance(this)
            clipboardItemsState.value = clipboardManager.clipboardItems.value
            quickSendItemsState.value = clipboardManager.quickSendItems.value

            serviceScope.launch {
                clipboardManager.clipboardItems.collect { items ->
                    clipboardItemsState.value = items
                }
            }
            serviceScope.launch {
                clipboardManager.quickSendItems.collect { items ->
                    quickSendItemsState.value = items
                    // 固定剪贴板不对插件开放：连条数变化也不投递 quick_send_changed 事件。
                }
            }
            serviceScope.launch {
                startClipboardSyncIfEnabled()
                PluginManager.pluginInstancesFlow.collect {
                    updateClipboardSync()
                    refreshToolbarPluginButtons()
                    closeToolPanelIfPluginGone()
                }
            }
            Log.d(TAG, "initClipboardManager: Clipboard manager initialized successfully")
        } catch (e: Exception) {
            FileLogger.e(TAG, "initClipboardManager: Failed to initialize clipboard manager", e)
        }
    }

    private fun startClipboardSyncIfEnabled() {
        if (clipboardSyncBridge != null) return
        try {
            if (!SettingsPreferences.isClipboardSyncEnabled(this)) {
                Log.d(TAG, "Clipboard sync disabled in settings")
                return
            }
            val enabled = ExtensionManager.getEnabledClipboardSyncPlugins(this)
            if (enabled.isEmpty()) return
            val preferredId = SettingsPreferences.getClipboardSyncPluginId(this)
            val selected = enabled.firstOrNull { it.first == preferredId } ?: enabled.first()
            val nativeWindows = selected.first == com.kingzcheung.xime.clipboard.sync.WindowsDevices.ID
            // 能力声明校验：未声明同步协议的插件不启动（manifest.capabilities.clipboard_sync.protocols）
            val syncCapabilities = ExtensionManager.getAllInstalledPlugins()
                .firstOrNull { it.id == selected.first }
                ?.capabilities?.clipboardSync
            if (!nativeWindows && syncCapabilities?.protocols.isNullOrEmpty()) {
                FileLogger.w(TAG, "Clipboard sync plugin ${selected.first} 未声明同步协议，拒绝启动")
                return
            }
            val plugin = selected.second
            clipboardSyncBridge = ClipboardSyncBridge(
                this,
                clipboardManager,
                plugin,
                pluginId = selected.first,
                foregroundPollIntervalMs = if (nativeWindows) 2000 else syncCapabilities?.foregroundPollIntervalMs ?: 0,
                maxTextBytes = if (nativeWindows) 65536 else syncCapabilities?.maxTextBytes ?: 0,
            )
            clipboardSyncBridge?.start()
            if (nativeWindows) com.kingzcheung.xime.clipboard.sync.WindowsDevices.start()
            clipboardSyncBridge?.setKeyboardVisible(isInputViewShown)
            uiState.value = uiState.value.copy(clipboardSyncEnabled = true)
            Log.d(TAG, "Clipboard sync started: ${selected.first}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to start clipboard sync", e)
        }
    }

    private fun stopClipboardSync() {
        if (clipboardSyncBridge?.pluginId == com.kingzcheung.xime.clipboard.sync.WindowsDevices.ID) {
            com.kingzcheung.xime.clipboard.sync.WindowsDevices.stopIfUnused()
        }
        clipboardSyncBridge?.release()
        clipboardSyncBridge = null
        uiState.value = uiState.value.copy(clipboardSyncEnabled = false)
    }

    /** 剪贴板同步设置或插件状态变化时调用，按条件动态启停。 */
    private fun updateClipboardSync() {
        if (!::clipboardManager.isInitialized) return
        if (clipboardSyncBridge == null) {
            startClipboardSyncIfEnabled()
            return
        }
        if (
            !SettingsPreferences.isClipboardSyncEnabled(this) ||
            ExtensionManager.getEnabledClipboardSyncPlugins(this).isEmpty()
        ) {
            stopClipboardSync()
            return
        }
        // 当前 bridge 使用的插件与偏好选中的插件不一致时，重启切换到偏好插件
        val enabled = ExtensionManager.getEnabledClipboardSyncPlugins(this)
        val preferredId = SettingsPreferences.getClipboardSyncPluginId(this)
        val shouldUse = (if (preferredId.isNotEmpty()) {
            enabled.firstOrNull { it.first == preferredId }
        } else null) ?: enabled.first()
        if (shouldUse.first != clipboardSyncBridge?.pluginId ||
            !clipboardSyncBridge!!.usesPlugin(shouldUse.second)) {
            stopClipboardSync()
            startClipboardSyncIfEnabled()
        }
    }

    /**
     * 刷新已启用插件声明的工具栏按钮到 uiState（插件启用/加载/卸载变化时调用）。
     * 两层显示控制的第一层：只有启用插件的按钮进入候选池；toolbar_buttons 偏好决定最终显示。
     */
    private fun refreshToolbarPluginButtons() {
        serviceScope.launch(Dispatchers.IO) {
            val buttons = ExtensionManager.getAllInstalledPlugins()
                .filter { SettingsPreferences.isPluginEnabled(this@XimeInputMethodService, it.id) }
                .flatMap { info ->
                    info.toolbarButtons.map { btn ->
                        ToolbarButtonItem.Plugin(
                            id = btn.id,
                            label = btn.label.ifBlank { btn.id },
                            icon = ExtensionManager.extractToolbarButtonIcon(
                                this@XimeInputMethodService, info.id, info, btn.icon
                            ) ?: ExtensionManager.extractPluginManifestIcon(this@XimeInputMethodService, info),
                            pluginId = info.id,
                            action = btn.action,
                        )
                    }
                }
            withContext(Dispatchers.Main) {
                uiState.value = uiState.value.copy(toolbarPluginButtons = buttons)
            }
        }
    }

    /** 面板打开时记录的输入框选区起止（上屏前恢复以替换原选区）。 */
    private var toolPanelSelection: Pair<Int, Int>? = null

    /** 面板生成轮询任务（流式生成期间持续刷新候选）。 */
    private var toolPanelPollJob: Job? = null

    /**
     * 插件工具栏按钮 action=open_panel 的宿主入口：打开该插件的通用面板。
     * 记录选区、按优先级收集上下文预填、向插件取初始面板状态并渲染。
     */
    internal fun openToolPanel(pluginId: String) {
        if (uiState.value.isFloatingMode ||
            resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        ) {
            Toast.makeText(this, "工具面板不支持在悬浮/横屏模式下使用", Toast.LENGTH_SHORT).show()
            return
        }
        if (uiState.value.showQuickSendForm) {
            uiState.value = uiState.value.copy(
                showQuickSendForm = false,
                quickSendFormFocused = false,
                quickSendCodeFocused = false,
                quickSendEditingItemId = null,
                quickSendEditingItemText = "",
                quickSendEditingItemCode = "",
            )
            QuickSendFormEditTextHolder.editText = null
            QuickSendFormCodeEditTextHolder.editText = null
        }
        // 打开面板前清理宿主输入框残留输入态（未上屏的拼音/英文），
        // 避免旧组合混入面板输入、或面板关闭后覆盖宿主输入框中段文字。
        val pending = candidateState.value
        if (pending.isComposing || pending.inputText.isNotEmpty() || pending.pendingEnglishText.isNotEmpty()) {
            rimeEngine.clearComposition()
            endComposingInputBox()
            candidateState.value = candidateState.value.copy(
                candidates = emptyList(),
                candidateComments = emptyList(),
                associationCandidates = emptyList(),
                pendingEnglishText = "",
                inputText = "",
                candidateActions = emptyList(),
                preeditText = "",
                isComposing = false,
                isShowingRecentClipboard = false,
                hasNextPage = false,
                hasPrevPage = false
            )
        }
        toolPanelSelection = readCurrentSelection()
        val contextText = collectToolPanelContext()
        val pluginName = ExtensionManager.getAllInstalledPlugins()
            .firstOrNull { it.id == pluginId }?.name ?: pluginId
        val display = ExtensionManager.getAllInstalledPlugins()
            .firstOrNull { it.id == pluginId }?.capabilities?.tool?.display
        val pluginState = (ExtensionManager.getPluginById(pluginId) as? ToolPlugin)
            ?.getPanelState(contextText)
        val prefill = pluginState?.inputText?.takeIf { it.isNotBlank() } ?: contextText
        uiState.value = uiState.value.copy(
            toolPanelVisible = true,
            toolPanelInputFocused = display != ToolResult.PASSIVE,
            toolPanelPluginId = pluginId,
            toolPanelTitle = pluginName,
            toolPanelPrefillText = prefill,
            toolPanelItems = pluginState?.items ?: emptyList(),
            toolPanelDisplay = display?.name,
            toolPanelUiNodes = pluginState?.ui,
            toolPanelRequestEpoch = uiState.value.toolPanelRequestEpoch + 1,
            enterKeyText = if (display == ToolResult.PASSIVE) "发送" else "生成",
        )
        if (display == ToolResult.PASSIVE) {
            // 纯展示面板与表情/符号同级：Overlay 全屏覆盖键盘，不撑高候选栏上方区域
            keyboardViewModel.showOverlay(OverlayRoute.ToolPanel)
        } else {
            ToolPanelEditTextHolder.editText?.let { et ->
                et.setText(prefill)
                et.setSelection(prefill.length)
            }
        }
    }

    /**
     * passive 纯展示面板的 action 点击：通知插件（onPanelAction）后单次重拉
     * getPanelState 刷新 ui 节点（action 改变数据后面板立即反映）。
     * 先置 loading 再执行：同步生成（插件 onPanelAction 阻塞返回）期间面板显示加载态。
     */
    internal fun dispatchToolPanelAction(actionId: String) {
        val pluginId = uiState.value.toolPanelPluginId
        val epoch = uiState.value.toolPanelRequestEpoch
        uiState.value = uiState.value.copy(toolPanelLoading = true)
        serviceScope.launch(Dispatchers.IO) {
            val plugin = ExtensionManager.getPluginById(pluginId) as? ToolPlugin ?: return@launch
            // runCatching：插件异常（Lua 超时/中毒）时不卡 loading，面板恢复可交互
            val state = runCatching {
                plugin.onPanelAction(actionId)
                plugin.getPanelState("")
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (uiState.value.toolPanelRequestEpoch == epoch && uiState.value.toolPanelVisible) {
                    uiState.value = uiState.value.copy(
                        toolPanelUiNodes = state?.ui,
                        toolPanelItems = state?.items ?: uiState.value.toolPanelItems,
                        toolPanelLoading = state?.loading ?: false,
                    )
                }
            }
        }
    }

    /** Learning state saved while the clipboard search bar keeps the engine incognito. */
    private var clipboardSearchSavedSensitive: Boolean? = null

    /**
     * Opens the clipboard search bar above the keyboard. It types through the tool-panel input
     * channel; the engine is incognito meanwhile, so nothing typed into search is learned.
     */
    internal fun openClipboardSearch() {
        if (uiState.value.toolPanelVisible) closeToolPanel()
        keyboardViewModel.closeOverlay()
        if (clipboardSearchSavedSensitive == null) {
            clipboardSearchSavedSensitive = com.kingzcheung.xime.settings.IncognitoState.sensitiveEditor
            com.kingzcheung.xime.settings.IncognitoState.sensitiveEditor = true
            applyIncognito()
        }
        clipboardManager.boardQuery.value = ""
        uiState.value = uiState.value.copy(clipboardSearchVisible = true, toolPanelInputFocused = true, enterKeyText = "完成")
    }

    internal fun closeClipboardSearch(openBoard: Boolean, keepQuery: Boolean = false) {
        if (!uiState.value.clipboardSearchVisible) return
        if (!keepQuery) clipboardManager.boardQuery.value = ""
        if (candidateState.value.isComposing) {
            keyRouter.postRimeJob { rimeEngine.clearComposition(); withContext(Dispatchers.Main) { updateUI() } }
        }
        uiState.value = uiState.value.copy(clipboardSearchVisible = false, toolPanelInputFocused = false,
            enterKeyText = editorEnterKeyText())
        ToolPanelEditTextHolder.editText = null
        clipboardSearchSavedSensitive?.let { com.kingzcheung.xime.settings.IncognitoState.sensitiveEditor = it }
        clipboardSearchSavedSensitive = null
        applyIncognito()
        if (openBoard) keyboardViewModel.showOverlay(OverlayRoute.Clipboard(0))
    }

    /** Enter in the search bar: the keyboard goes away and the clipboard keeps showing the matches. */
    internal fun submitClipboardSearch() {
        clipboardManager.boardQuery.value = ToolPanelEditTextHolder.editText?.text?.toString()?.trim().orEmpty()
        closeClipboardSearch(openBoard = true, keepQuery = true)
    }

    /** A result goes through the normal clipboard paste, so a fixed record stays private. */
    internal fun selectClipboardSearchResult(text: String) {
        closeClipboardSearch(openBoard = false)
        textCommit.selectClipboardItem(text)
    }

    internal fun closeToolPanel() {        toolPanelSelection = null
        stopToolPanelPoll()
        // 面板相关 Overlay 页面（ToolPanel 纯展示页）开着时联动关闭
        val page = keyboardViewModel.page.value
        if (page is com.kingzcheung.xime.keyboard.KeyboardPage.Overlay &&
            page.route == OverlayRoute.ToolPanel
        ) {
            keyboardViewModel.closeOverlay()
        }
        uiState.value = uiState.value.copy(
            toolPanelVisible = false,
            toolPanelInputFocused = false,
            toolPanelPluginId = "",
            toolPanelTitle = "",
            toolPanelPrefillText = "",
            toolPanelItems = emptyList(),
            toolPanelDisplay = null,
            toolPanelUiNodes = null,
            toolPanelLoading = false,
            enterKeyText = editorEnterKeyText(),
        )
        ToolPanelEditTextHolder.editText = null
    }

    /**
     * 面板候选条目上屏：有选区时先恢复选区再提交（替换原选区），无选区时光标处追加。
     * 直接经 InputConnection 提交，不走 commitText 重定向（避免注入回面板输入框）。
     */
    internal fun commitToolPanelItem(text: String) {
        val pluginId = uiState.value.toolPanelPluginId
        val itemId = uiState.value.toolPanelItems.firstOrNull { it.text == text }?.id ?: text
        serviceScope.launch(Dispatchers.IO) {
            (ExtensionManager.getPluginById(pluginId) as? ToolPlugin)?.onPanelItemClick(itemId)
        }
        val ic = currentInputConnection ?: return
        toolPanelSelection?.let { (start, end) ->
            runCatching { ic.setSelection(start, end) }
        }
        ic.commitText(text, 1)
        if (isChineseMode) {
            predictionManager.appendCommittedText(text)
            predictionManager.recordInput(text)
        }
        closeToolPanel()
    }

    /**
     * 面板触发生成：通知插件输入变化并触发 generate，异步轮询取回最新面板状态。
     * 同步生成（智能回复）阻塞返回后一次取回；流式生成（帮写）期间插件 loading=true，
     * 宿主持续轮询刷新，直到 loading 结束或面板关闭。
     * 代际号防旧结果回填：新请求/重开会递增 epoch，旧结果回来时检测到已过期则丢弃。
     */
    internal fun triggerToolPanelGenerate() {
        val pluginId = uiState.value.toolPanelPluginId
        // passive 纯展示面板：无生成语义，enter 不触发（数据由事件驱动 + InfoPanel 点击 action 刷新）
        if (uiState.value.toolPanelDisplay == ToolResult.PASSIVE.name) {
            return
        }
        // 使用前授权检测：插件有未授权网络域名时先引导授权，不发起请求
        if (!com.kingzcheung.xime.plugin.PluginNetworkAuthHelper.ensureAuthorized(this, pluginId)) {
            return
        }
        val inputText = ToolPanelEditTextHolder.editText?.text?.toString() ?: ""
        // 捕获当前代际号（openToolPanel 时递增）。轮询期间持续对比：
        // 面板被重新打开（epoch 递增）即视为过期，丢弃本轮结果。
        val epoch = uiState.value.toolPanelRequestEpoch
        val epochStartTime = System.currentTimeMillis()
        toolPanelPollJob?.cancel()
        toolPanelPollJob = serviceScope.launch(Dispatchers.IO) {
            val plugin = ExtensionManager.getPluginById(pluginId) as? ToolPlugin
            plugin?.onPanelInput(inputText)
            plugin?.onPanelAction("generate")
            while (uiState.value.toolPanelVisible) {
                val state = plugin?.getPanelState(inputText) ?: break
                val items = state.items
                val loading = state.loading
                withContext(Dispatchers.Main) {
                    if (uiState.value.toolPanelRequestEpoch == epoch) {
                        uiState.value = uiState.value.copy(
                            toolPanelItems = items,
                            toolPanelLoading = loading,
                        )
                    }
                }
                if (!loading) break
                delay(200)
            }
            // 结果交互：生成结束后按结果显示方式决策——
            //   display=DIRECT → 直接上屏替换选区并关闭面板（AI 翻译/帮写等单结果场景，无需点击）
            //   display=PASSIVE → 面板本身全屏展示 items（InfoPanel 点选上屏），无需分派
            //   display=null（未声明）→ 直接上屏（旧行为为按数量兜底开页面，页面已并入 passive 面板）
            //   空结果 → 保持面板（用户可重新生成）
            withContext(Dispatchers.Main) {
                if (uiState.value.toolPanelVisible) {
                    val items = uiState.value.toolPanelItems
                    when {
                        items.isEmpty() -> {
                            // 空结果：若非静默失败（插件刚记录了错误），Toast 告知用户原因
                            val lastError = com.kingzcheung.xime.plugin.core.security.PluginErrorLog
                                .getLastError(pluginId)
                            val errorMessage = lastError?.message
                            if (!errorMessage.isNullOrEmpty() &&
                                lastError.timestamp >= epochStartTime
                            ) {
                                android.widget.Toast.makeText(
                                    this@XimeInputMethodService,
                                    errorMessage,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> commitToolPanelItem(items[0].text)
                    }
                }
            }
        }
    }

    private fun stopToolPanelPoll() {
        toolPanelPollJob?.cancel()
        toolPanelPollJob = null
    }

    /**
     * 面板所属插件被禁用/卸载时自动关闭面板（工具栏按钮候选池同步移除，
     * toolbar_buttons 偏好中残留 id 匹配不到自然不显示，无需清理偏好）。
     */
    private fun closeToolPanelIfPluginGone() {
        val state = uiState.value
        if (!state.toolPanelVisible) return
        val pluginId = state.toolPanelPluginId
        if (pluginId.isBlank()) return
        val stillEnabled = ExtensionManager.getAllInstalledPlugins().any {
            it.id == pluginId && SettingsPreferences.isPluginEnabled(this, it.id)
        }
        if (!stillEnabled) {
            closeToolPanel()
        }
    }

    /** 读取当前输入框选区（起止不等时返回，供上屏替换原选区）。 */
    private fun readCurrentSelection(): Pair<Int, Int>? {
        val ic = currentInputConnection ?: return null
        return runCatching {
            val req = android.view.inputmethod.ExtractedTextRequest()
            val extracted = ic.getExtractedText(req, 0)
            if (extracted != null && extracted.selectionStart >= 0 && extracted.selectionEnd > extracted.selectionStart) {
                Pair(extracted.selectionStart, extracted.selectionEnd)
            } else null
        }.getOrNull()
    }

    /** 收集面板上下文：仅选中文本；未选中时返回空串（不预填输入框全文/剪贴板，待用户自行输入）。 */
    /**
     * 工具面板上下文收集（选区 > 输入框选区 > 剪贴板）：
     * 对方消息通常来自聊天 App 复制而非输入框选区，剪贴板兜底是 AI 回复等
     * 插件拿到上下文的关键路径（插件契约见 plugins/ai-reply/main.lua）。
     */
    private fun collectToolPanelContext(): String {
        val ic = currentInputConnection
        if (ic != null) {
            runCatching {
                val sel = ic.getSelectedText(0)?.toString()
                if (!sel.isNullOrBlank()) return sel
            }
            runCatching {
                val req = android.view.inputmethod.ExtractedTextRequest()
                val extracted = ic.getExtractedText(req, 0)
                if (extracted != null && extracted.selectionStart >= 0 && extracted.selectionEnd > extracted.selectionStart) {
                    val t = extracted.text?.toString()
                    if (t != null) {
                        val s = extracted.selectionStart.coerceIn(0, t.length)
                        val e = extracted.selectionEnd.coerceIn(s, t.length)
                        if (e > s) return t.substring(s, e)
                    }
                }
            }
        }
        // 剪贴板兜底：无选区/无输入连接时取系统剪贴板
        runCatching {
            if (::clipboardManager.isInitialized) {
                clipboardManager.getCurrentClipboardText()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    private fun ensureClipboardManagerInitialized() {
        if (!::clipboardManager.isInitialized) {
            Log.d(TAG, "ensureClipboardManagerInitialized: Initializing clipboard manager synchronously")
            try {
                clipboardManager = ClipboardManager.getInstance(this)
                clipboardItemsState.value = clipboardManager.clipboardItems.value
                quickSendItemsState.value = clipboardManager.quickSendItems.value
                Log.d(TAG, "ensureClipboardManagerInitialized: Clipboard manager initialized")
            } catch (e: Exception) {
                FileLogger.e(TAG, "ensureClipboardManagerInitialized: Failed to initialize clipboard manager", e)
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboardContainer = VoiceKeyboardContainer(
            context = this,
            acceptTouchDown = { downTime -> uiState.value.isDeploying || inputReadiness.acceptsEvent(downTime) },
            uiStateProvider = { uiState.value },
            onUiStateChanged = { newState -> uiState.value = newState },
            onPerformVibration = { view -> feedbackManager.hapticFeedback(view) },
            onPerformUndo = { pendingVoiceAction = { textCommit.performUndo() } },
            onPerformSearch = { pendingVoiceAction = { textCommit.performSearch() } },
            onStopRecognition = {
                endVoiceSession()
            },
            isRecording = { voiceRecordingStarted },
            setRecording = { voiceRecordingStarted = it },
            onVoiceDismiss = {
                val action = pendingVoiceAction
                pendingVoiceAction = null
                action?.invoke()
                endVoiceSession()
            },
            onTouchCancel = {
                uiState.value = uiState.value.copy(
                    swipeCancelEpoch = uiState.value.swipeCancelEpoch + 1
                )
            }
        )
        // 编码气泡绘制在候选栏上方（栏外，drawBehind 负坐标）：insets 重构后容器
        // 物理高度 = Compose 内容总高（gravity BOTTOM），候选栏紧贴容器顶边，
        // 气泡落在容器顶边之外——关闭容器裁剪，气泡才能画进 inputArea 的空白区。
        // 仅影响越界绘制裁剪，不触碰容器高度/insets 计算路径。
        keyboardContainer.clipChildren = false

        bottomInsetPxState.value = getActiveBottomInsetPx(window.window)
        keyboardContainer.setOnApplyWindowInsetsListener { v, insets ->
            val px = extractBottomInset(insets)
            if (px != bottomInsetPxState.value) {
                bottomInsetPxState.value = px
            }
            v.onApplyWindowInsets(insets)
        }

        val composeView = ComposeView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            composeViewRef = this
            setContent {
              androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val inputWindowWidthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
                val cand = candidateState.value
                val state = uiState.value
                // Reloaded key gaps/rows and split changes affect automatic geometry even
                // when the active input profile and stored dimensions remain unchanged.
                KeysConfigHelper.configVersion.collectAsState().value
                com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences().splitKeyboardEnabled
                val frostedGlass = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences(isDarkTheme()).frostedGlass
                // 尺寸调整中的透明度控制整块键盘；玻璃表面参数仍由独立设置管理。
                val keyboardLayerOpacity = state.keyboardOpacity
                val page by keyboardViewModel.page.collectAsState(com.kingzcheung.xime.keyboard.KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL))
                LaunchedEffect(page) {
                    if ((page as? KeyboardPage.Overlay)?.route !is OverlayRoute.Clipboard && clipboardKeyboard.active) {
                        clipboardKeyboard.reset()
                        clipboardRestoreCompact = false
                    }
                }
                val isHandwritingMode = (page.underlyingPage() as? KeyboardPage.Main)?.type == com.kingzcheung.xime.keyboard.MainType.HANDWRITING
                val handwritingExpansion = com.kingzcheung.xime.ui.keyboard.rememberHandwritingExpansion(
                    isHandwritingMode && !state.showKeyboardResize && !state.isCompact,
                    state.handwritingExpanded, state.inputSessionId)
                val handwritingExpanded = handwritingExpansion.active
                val clipboardPanelOpen = clipboardPanelCanExpand(page, state.showKeyboardResize, state.isCompact)
                val imagePanelExpanded = clipboardPanelOpen && state.clipboardImagesExpanded
                androidx.compose.runtime.LaunchedEffect(isHandwritingMode) {
                    if (!isHandwritingMode && uiState.value.handwritingExpanded)
                        uiState.value = uiState.value.copy(handwritingExpanded = false)
                }
                val isDarkTheme = isDarkTheme()
                val roundedKeyboardBottom = com.kingzcheung.xime.ui.keyboard.rememberRoundedKeyboardBottom()
                SideEffect {
                    updateGlassOpacity(if (state.isCompact || state.voiceCollapsed) 1f else keyboardLayerOpacity)
                    updateGlassConfiguration(frostedGlass, when {
                        state.isCompact || state.voiceCollapsed -> "hardware"
                        state.isFloatingMode -> "floating"
                        else -> "fixed"
                    })
                    if (state.isCompact || state.voiceCollapsed || state.isFloatingMode || roundedKeyboardBottom) {
                        updateGlassSurface("navigation", null)
                    }
                }
                val screenHeightDp = resources.configuration.screenHeightDp
                val physicalScreenDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt()
                val statusBarHeightDp = tryGetStatusBarHeightDp(this@XimeInputMethodService, window.window)
                val visibleNavBarHeightDp = tryGetVisibleNavBarHeightDp(this@XimeInputMethodService, window.window)
                // 用物理屏幕高度减去状态栏，保证不同 Android 版本一致
                val effectiveScreenH = if (state.isFloatingMode || state.showKeyboardResize || state.isCompact || state.voiceCollapsed) (physicalScreenDp - statusBarHeightDp).coerceAtLeast(1) else screenHeightDp
                val windowVisibleHeightDp = effectiveScreenH
                // 浮动窗口使用物理屏高，底部始终预留实际系统栏；不能根据配置屏高猜测已扣除。
                val floatingMinY = maxOf(visibleNavBarHeightDp,
                    kotlin.math.ceil(bottomInsetPxState.value / resources.displayMetrics.density).toInt())
                val callbacks = rememberImeKeyboardCallbacks(this@XimeInputMethodService, floatingMinY, state, effectiveScreenH)
                keyboardCallbacks = callbacks
                // The input controller belongs to the editor session, including compact-only starts.
                keyboardViewModel.bindT9Controller(callbacks)

                val screenWidthDp = inputWindowWidthDp
                val screenIsLandscape = screenWidthDp > screenHeightDp
                val heightBounds = KeyboardHeightProfiles.fixedBounds(this@XimeInputMethodService, screenIsLandscape)
                val geometryBottomSpace = if (state.isFloatingMode) floatingMinY else
                    state.keyboardBottomPaddingDp + imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density)
                val protectedSize = resolveKeyboardSize(screenWidthDp, effectiveScreenH, geometryBottomSpace, state)
                val resetSize = resolveKeyboardSize(screenWidthDp, effectiveScreenH,
                    if (state.isFloatingMode) floatingMinY else imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density),
                    state, reset = true)
                val keyboardHeight = protectedSize.height
                val handwritingFraction = handwritingExpansion.fraction
                fun handwritingSize(normal: Int, full: Int) = (normal + (full - normal) * handwritingFraction).roundToInt()
                val handwritingWidth = handwritingSize(protectedSize.width, screenWidthDp)
                val handwritingNormalX = if (state.isFloatingMode) state.floatingOffsetX else protectedSize.offsetX
                val handwritingOffsetX = handwritingSize(handwritingNormalX, 0)
                val contentBottomPaddingDp = if (handwritingExpanded || state.isFloatingMode) 0 else state.keyboardBottomPaddingDp
                val floatingDragBarHeight = if (state.isFloatingMode && !handwritingExpanded) FLOATING_DRAG_BAR_HEIGHT_DP else 0
                // Keep a floating panel's bottom in place while it grows, and reserve its drag bar.
                val clipboardBottomSpace = if (state.isFloatingMode) {
                    state.floatingOffsetY.coerceIn(floatingMinY,
                        (effectiveScreenH - keyboardHeight - floatingDragBarHeight).coerceAtLeast(floatingMinY)) + floatingDragBarHeight
                } else floatingMinY + contentBottomPaddingDp
                val clipboardExpansion = rememberClipboardPanelExpansion(
                    clipboardPanelOpen, state.inputSessionId,
                    clipboardPanelBounds(keyboardHeight, effectiveScreenH, clipboardBottomSpace),
                    state.clipboardImagesExpanded,
                ) { expanded ->
                    uiState.value = uiState.value.copy(clipboardImagesExpanded = expanded)
                }
                // Clipboard search keeps the keyboard as it is: the results take the space the clipboard
                // would take when pulled up, above the keyboard and the search bar.
                // The container grows with the same pull-up animation, so the keyboard below never moves.
                val clipboardSearchTarget = com.kingzcheung.xime.ui.keyboard.clipboardSearchResultsTarget(
                    state.clipboardSearchVisible, clipboardPanelBounds(keyboardHeight, effectiveScreenH, clipboardBottomSpace), keyboardHeight)
                val clipboardSearchResultsHeight = androidx.compose.animation.core.animateIntAsState(clipboardSearchTarget,
                    com.kingzcheung.xime.ui.keyboard.keyboardPanelMotion(clipboardSearchTarget > 0), label = "clipboardSearch").value
                // 浮动只收窄宽度，不再压缩按键行高或带入普通模式的底部留白。
                val effectiveKeyboardHeight = when {
                    handwritingExpanded -> handwritingSize(keyboardHeight, (effectiveScreenH - floatingMinY).coerceAtLeast(1))
                    clipboardExpansion != null -> clipboardExpansion.height.roundToInt()
                    else -> keyboardHeight
                }
                val floatingCardContentHeight = effectiveKeyboardHeight + floatingDragBarHeight
                
                val density = LocalDensity.current
                // 统一使用 View 层多类型检测的 insets，避免 Compose
                // navigationBars 恒为手势条高度导致与系统栏（三键导航）差异被抹平。
                val activeBottomDp = imeBottomSpaceDp(bottomInsetPxState.value, density.density)
                val navBarDp = activeBottomDp.dp
                val hasNavBar = navBarDp > 0.dp

                // 工具面板为"键盘上方的撑高面板"：显示时键盘总高增加面板高度（面板在键盘上方，
                // 不遮键盘按键），同时容器物理高度同步变大（updateHeight）→ IME insets 由系统确定性重算，
                // 关闭后容器还原，彻底避免 insets 残留与白色区域。
                // Overlay 页面（menubar/剪贴板/emoji 等）全屏覆盖键盘内容区：激活期间撑高面板
                // 不参与计算，否则 Overlay 页面会带上表单/工具面板的额外高度（容器整体被撑高）。
                val isOverlayPage = page is com.kingzcheung.xime.keyboard.KeyboardPage.Overlay
                // Clipboard editing uses a separate popup and does not resize the keyboard.
                // 需与 ToolPanel.TOOL_PANEL_HEIGHT(170) 保持一致，否则容器比面板多/少一截，键盘被拉高。
                // PASSIVE 纯展示面板走 Overlay 全屏覆盖（键盘窗口内容区），不撑高。
                val toolPanelExtra = if (state.toolPanelVisible &&
                    state.toolPanelDisplay != "PASSIVE" &&
                    !isOverlayPage
                ) 170 else 0
                // Clipboard search adds its results and search panel above the keyboard like the tool panel.
                val overlayPanelExtra = toolPanelExtra + com.kingzcheung.xime.ui.keyboard.clipboardSearchExtraHeight(
                    state.clipboardSearchVisible, isOverlayPage, clipboardSearchResultsHeight)

                val isDark = isDarkTheme
                val hardwarePalette = com.kingzcheung.xime.ui.theme.resolveKeyboardPalette(state.themeId, isDark)
                val cardBg = hardwarePalette.background
                val keyboardBgColor = com.kingzcheung.xime.ui.theme.KeyboardThemes.getKeyboardBackgroundColor(state.themeId, isDark)
                val rootTheme = com.kingzcheung.xime.ui.theme.KeyboardThemes.getRenderingScheme(state.themeId)
                val contentHeight = if (state.showKeyboardResize) state.resizePreviewHeightDp else floatingCardContentHeight + overlayPanelExtra
                XimeTheme(darkTheme = isDarkTheme, themeId = state.themeId) {
                    com.kingzcheung.xime.ui.theme.KeyboardBackdropHost(
                        enabled = frostedGlass.enabled && !state.isCompact && !state.voiceCollapsed && !state.isFloatingMode && !roundedKeyboardBottom,
                        background = rootTheme.keyboardBackground, isDark = isDark,
                        fallback = cardBg, glass = frostedGlass,
                        backdropHeight = (contentHeight + contentBottomPaddingDp + activeBottomDp).dp,
                        modifier = Modifier.fillMaxSize(),
                        clipboardInMotion = clipboardExpansion?.isInMotion == true,
                    ) {
                        // Sync FrameLayout height with Compose content height
                        val totalDp = if (handwritingExpanded || state.showKeyboardResize || state.isCompact || state.voiceCollapsed || state.isFloatingMode) effectiveScreenH
                            else contentHeight + contentBottomPaddingDp + activeBottomDp
                        SideEffect {
                            currentResolvedKeyboardSize = protectedSize
                            // 容器物理高度 = Compose 内容总高（含底部留白），全模式统一。
                            // 容器高度变化 → View 层 relayout → traversal → onComputeInsets
                            // 自动以新高度重算并上报（ViewRootImpl 每次 traversal 都 dispatch
                            // OnComputeInternalInsetsListener，值变化即 setInsets），
                            // 无需 +1dp hack 强制造型变化。
                            handwritingNormalInsetDp = keyboardHeight + state.keyboardBottomPaddingDp + activeBottomDp
                            handwritingOverlayActive = handwritingExpanded
                            keyboardContainer.updateHeight(totalDp)
                            currentEffectiveKeyboardHeight = if (state.isFloatingMode) floatingCardContentHeight + overlayPanelExtra
                                else if (state.isCompact) HARDWARE_CANDIDATE_BAR_HEIGHT
                                else effectiveKeyboardHeight + overlayPanelExtra
                        }
                        if (state.isCompact) {
                          androidx.compose.runtime.CompositionLocalProvider(
                              com.kingzcheung.xime.ui.theme.LocalMaterialPalette provides
                                  com.kingzcheung.xime.ui.theme.MaterialPalette(cardBg, hardwarePalette.accent)) {
                          androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = activeBottomDp.dp)) {
                            SideEffect {
                                hardwareViewportWidthDp = maxWidth.value
                                hardwareViewportHeightDp = maxHeight.value
                            }
                            val bottomDocked = hardwareToolbarPosition.value.isBottomDocked(state.hardwareOptions.dockAtEdge) && maxWidth >= 336.dp
                            val glass = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences(isDark).frostedGlass
                            // The keyboard backdrop supplies theme/glass color and opacity. Do not
                            // add visualMaterial here: its rim redraws an unwanted outline around
                            // both the tight preedit preview and the single-row candidate surface.
                            val hardwareSurface: @Composable () -> Modifier = {
                                Modifier.keyboardBackground(rootTheme.keyboardBackground, isDark, cardBg,
                                    frostedGlass = glass, translucentSurface = true)
                            }
                            val hardwareEnglish = state.isAsciiMode || state.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.ENGLISH
                            val pendingPrediction by predictionManager.pendingRequest.collectAsState()
                            // The toolbar numbers 10 candidates; a longer engine page is shown in windows of 10.
                            val hardwareWindow = keyRouter.hardwareWindowStart(cand)
                            com.kingzcheung.xime.gloss.CandidateGlossary.revision // 词典加载完成后重组
                            val hardwareCandidates = hardwareCandidatePresentation(cand, hardwareEnglish,
                                predictionPending = pendingPrediction != 0L).window(hardwareWindow).let { shown ->
                                    // 释义只换显示用注释，与屏幕候选栏同一规则。
                                    if (hardwareEnglish || shown.association ||
                                        state.inputProfile.language != com.kingzcheung.xime.settings.InputLanguage.CHINESE) shown
                                    else if (com.kingzcheung.xime.gloss.CandidateGlossary.physicalShiftHeld.value) {
                                        val (words, notes) = com.kingzcheung.xime.gloss.CandidateGlossary.swapped(shown.words, shown.comments)
                                        shown.copy(words = words, comments = notes)
                                    }
                                    else shown.copy(comments = com.kingzcheung.xime.gloss.CandidateGlossary.annotate(shown.words, shown.comments))
                                }
                            val hardwareHighlight = keyRouter.hardwareCandidateHighlight(cand, hardwareEnglish,
                                predictionPending = pendingPrediction != 0L)
                            val hardwareLanguage = if (hardwareEnglish) "EN" else when (state.inputProfile.language) {
                                com.kingzcheung.xime.settings.InputLanguage.CHINESE -> "中"
                                com.kingzcheung.xime.settings.InputLanguage.JAPANESE -> "あ"
                                else -> state.inputProfile.language.displayName
                            }
                            val selectHardwareCandidate: (Int) -> Unit = { index ->
                                if (hardwareCandidates.association) {
                                    val current = candidateState.value
                                    if ((hardwareEnglish || !predictionManager.hasPendingPrediction) &&
                                        state.inputSessionId == uiState.value.inputSessionId &&
                                        cand.pendingEnglishText == current.pendingEnglishText &&
                                        cand.associationCandidates == current.associationCandidates) {
                                        keyboardCallbacks?.onAssociationSelect?.invoke(index)
                                    }
                                } else if (cand.hasSameSelectionSource(candidateState.value)) {
                                    keyRouter.selectCandidate(hardwareWindow + index)
                                    keyRouter.resetHardwareCandidateSelection()
                                }
                            }
                            val previousHardwareCandidate: () -> Unit = {
                                if (cand.hasSameSelectionSource(candidateState.value)) {
                                    keyRouter.moveHardwareCandidate(-1)
                                }
                            }
                            val nextHardwareCandidate: () -> Unit = {
                                if (cand.hasSameSelectionSource(candidateState.value)) {
                                    keyRouter.moveHardwareCandidate(1)
                                }
                            }
                            HardwareKeyboardCandidateBar(
                                frostedGlass = glass,
                                inputText = if (hardwareEnglish) "" else cand.inputText,
                                preeditText = if (hardwareEnglish) "" else cand.preeditText,
                                candidates = hardwareCandidates.standaloneWords,
                                hasNextPage = hardwareCandidates.hasNextPage,
                                hasPrevPage = hardwareCandidates.hasPreviousPage,
                                cursorAnchor = state.cursorAnchor.takeIf { state.hardwareOptions.followCursor },
                                highlightIndex = hardwareHighlight,
                                cardBackgroundColor = cardBg,
                                candidateTextColor = hardwarePalette.candidateText,
                                activeColor = hardwarePalette.accent,
                                selectedTextColor = hardwarePalette.selectedText,
                                preeditBackgroundColor = hardwarePalette.key,
                                comments = hardwareCandidates.comments,
                                onPrevious = previousHardwareCandidate.takeUnless { hardwareCandidates.association },
                                onNext = nextHardwareCandidate.takeUnless { hardwareCandidates.association },
                                onCandidateSelect = selectHardwareCandidate,
                                bottomDocked = bottomDocked,
                                showNumberLabels = true,
                                surface = hardwareSurface,
                                onPreeditBoundsChanged = { updateHardwarePreeditBounds(it) },
                                onBoundsChanged = { updateHardwareSurfaceBounds(toolbar = false, it) },
                                avoidBoundsInWindow = hardwareToolbarBounds.value,
                                avoidCaret = state.cursorAnchor.takeIf { state.hardwareOptions.avoidEditor },
                            )
                            HardwareKeyboardToolbar(
                                frostedGlass = glass,
                                selectedTextColor = hardwarePalette.selectedText,
                                accentColor = hardwarePalette.accent,
                                onShowKeyboard = { showScreenKeyboardFromHardware() },
                                onEmoji = { showScreenKeyboardFromHardware(OverlayRoute.Emoji) },
                                onClipboard = { showScreenKeyboardFromHardware(OverlayRoute.Clipboard()) },
                                onVoice = { handleHardwareTool(HardwareToolShortcut.MICROPHONE) },
                                voiceActive = state.isVoiceMode,
                                onSwitchLanguage = { switchHardwareLanguage() },
                                languageLabel = hardwareLanguage,
                                statusText = if (state.isDeploying) "词库加载中，完成后即可输入中文" else null,
                                languageActions = com.kingzcheung.xime.ui.keyboard.KeyboardInputActions(
                                    schemas = com.kingzcheung.xime.settings.InputModes.available(state.schemas),
                                    currentInputModeId = com.kingzcheung.xime.settings.InputModes.selectedId(state.currentSchemaId, state.isAsciiMode),
                                    onSwitchSchema = { id -> if (inputReadiness.ticket() != null) schemaController.switchSchema(id) },
                                ),
                                cursorAnchor = state.cursorAnchor,
                                editorBounds = state.editorBounds,
                                avoidEditor = state.hardwareOptions.avoidEditor,
                                dockAtEdge = state.hardwareOptions.dockAtEdge,
                                // 吸附底边时的样式：默认为现在的长条，可选三星式贴底通栏。
                                samsungBottomBar = state.hardwareOptions.bottomBar,
                                onHideKeyboard = { requestHideSelf(0) },
                                capsLock = hardwareCapsLockState.value,
                                backgroundColor = cardBg,
                                surface = hardwareSurface,
                                bottomDocked = bottomDocked,
                                bottomCandidateContent = if (hardwareCandidates.words.isEmpty()) null else {
                                  {
                                    HardwareCandidateRow(if (state.hardwareOptions.bottomBar) hardwareCandidates.words.take(9)
                                        else hardwareCandidates.words, hardwareCandidates.comments,
                                        hardwareHighlight,
                                        hardwareCandidates.hasPreviousPage, hardwareCandidates.hasNextPage,
                                        CandidateBarVisuals(cardBg, hardwarePalette.candidateText, Color.Transparent,
                                            hardwarePalette.accent, hardwarePalette.selectedText),
                                        selectHardwareCandidate,
                                        previousHardwareCandidate.takeUnless { hardwareCandidates.association },
                                        nextHardwareCandidate.takeUnless { hardwareCandidates.association },
                                        showNumberLabels = !hardwareEnglish)
                                  }
                                },
                                showCandidatesWhenFloating = hardwareCandidates.association,
                                candidatesPending = !hardwareEnglish && pendingPrediction != 0L,
                                contentColor = hardwarePalette.candidateText,
                                position = hardwareToolbarPosition.value,
                                onPositionChange = { hardwareToolbarPosition.value = it },
                                onBoundsChanged = { updateHardwareSurfaceBounds(toolbar = true, it) },
                            )
                          }
                          }
                        } else if (state.voiceCollapsed) {
                          // 语音收起：键盘让位，只留底部悬浮条；外观沿用实体键盘浮条（主题 + 毛玻璃）。
                          androidx.compose.runtime.CompositionLocalProvider(
                              com.kingzcheung.xime.ui.theme.LocalMaterialPalette provides
                                  com.kingzcheung.xime.ui.theme.MaterialPalette(cardBg, hardwarePalette.accent)) {
                          Box(Modifier.fillMaxSize().padding(bottom = activeBottomDp.dp)) {
                            androidx.compose.runtime.DisposableEffect(Unit) {
                                voicePillOnScreen = true
                                onDispose { voicePillOnScreen = false }
                            }
                            val glass = com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences(isDark).frostedGlass
                            val pillEnglish = state.isAsciiMode || state.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.ENGLISH
                            com.kingzcheung.xime.ui.keyboard.VoiceCollapsedPill(
                                listening = state.isVoiceMode,
                                amplitude = { voiceAmplitudeState.floatValue },
                                languageLabel = if (pillEnglish) "EN" else when (state.inputProfile.language) {
                                    com.kingzcheung.xime.settings.InputLanguage.CHINESE -> "中"
                                    com.kingzcheung.xime.settings.InputLanguage.JAPANESE -> "あ"
                                    else -> state.inputProfile.language.displayName
                                },
                                languageActions = com.kingzcheung.xime.ui.keyboard.KeyboardInputActions(
                                    schemas = com.kingzcheung.xime.settings.InputModes.available(state.schemas),
                                    currentInputModeId = com.kingzcheung.xime.settings.InputModes.selectedId(state.currentSchemaId, state.isAsciiMode),
                                    onSwitchSchema = { id -> if (inputReadiness.ticket() != null) schemaController.switchSchema(id) },
                                ),
                                onSwitchLanguage = { switchHardwareLanguage() },
                                onShowKeyboard = { expandFromVoicePill() },
                                onBackspace = { keyboardCallbacks?.onKeyPress("delete", false) },
                                onBackspacePress = { keyboardCallbacks?.onKeyPressDown?.invoke("delete") },
                                onBackspaceRelease = { keyboardCallbacks?.onKeyRelease?.invoke("delete") },
                                onMic = { keyboardCallbacks?.onVoiceStickyToggle?.invoke() },
                                backgroundColor = cardBg,
                                contentColor = hardwarePalette.candidateText,
                                selectedTextColor = hardwarePalette.selectedText,
                                accentColor = hardwarePalette.accent,
                                frostedGlass = glass,
                                surface = {
                                    Modifier.keyboardBackground(rootTheme.keyboardBackground, isDark, cardBg,
                                        frostedGlass = glass, translucentSurface = true)
                                },
                                onBoundsChanged = { updateHardwareSurfaceBounds(toolbar = true, it) },
                            )
                          }
                          }
                        } else {
                        // The navigation strip and keyboard sample one backdrop in window coordinates;
                        // this strip draws only its own slice, never a separately fitted glass image.
                        // 键盘内容通过 offset 上移 activeBottomDp 留出导航栏空间。
                        // 浮动模式：卡片由 KeyboardView 内部 FloatingKeyboardContainer 自绘背景与定位，此处不做背景/偏移。
                        if (!state.isFloatingMode && !roundedKeyboardBottom) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(activeBottomDp.dp)
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .onGloballyPositioned { coordinates ->
                                        val position = coordinates.positionInWindow()
                                        updateGlassSurface("navigation", android.graphics.Rect(
                                            position.x.roundToInt(), position.y.roundToInt(),
                                            (position.x + coordinates.size.width).roundToInt(),
                                            (position.y + coordinates.size.height).roundToInt()))
                                    }
                                    .graphicsLayer { alpha = keyboardLayerOpacity }
                                    .keyboardBackground(rootTheme.keyboardBackground, isDark, keyboardBgColor)
                            )
                        }
                        Box(
                            modifier = if (handwritingExpanded) Modifier.fillMaxWidth().height(effectiveKeyboardHeight.dp)
                                .align(androidx.compose.ui.Alignment.BottomCenter).offset(y = (-handwritingSize(
                                    if (state.isFloatingMode) state.floatingOffsetY.coerceAtLeast(floatingMinY) else activeBottomDp,
                                    activeBottomDp)).dp)
                            else if (com.kingzcheung.xime.ui.keyboard.usesFullKeyboardHost(
                                state.isFloatingMode, state.showKeyboardResize
                            )) {
                                // 正常悬浮也必须全高。确认后缩成键盘高度会让上移的卡片被图层裁掉。
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height((floatingCardContentHeight + contentBottomPaddingDp + overlayPanelExtra).dp)
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .then(if (state.isFloatingMode) Modifier else Modifier.offset(y = (-activeBottomDp).dp))
                            }
                        ) {
                        CompositionLocalProvider(
                            com.kingzcheung.xime.clipboard.LocalClipboardKeyboardNavigation provides clipboardKeyboard,
                            LocalStretchFactor provides state.stretchFactor,
                            LocalClipboardPanelExpansion provides clipboardExpansion,
                        ) {
                            // 注意：kbState 只承载键盘按键/布局状态，不承载候选数据。
                            // 候选数据单独通过 candidateState 传给 KeyboardView。
                            // remember 的 key 均为候选无关依赖：候选变化时 kbState 实例保持不变，
                            // KeyboardView（按键区）跳过重组，只有读取 candidateState 的候选栏重组，
                            // 避免长按退格时高频候选更新触发整个键盘重组导致候选栏闪烁。
                            val kbState = remember(
                                state,
                                isDarkTheme,
                                effectiveKeyboardHeight,
                                // The search results open with the container's animation; without this key the
                                // keyboard view kept the first frame (0) and the keys stretched into the results.
                                clipboardSearchResultsHeight,
                                handwritingExpanded, handwritingWidth, handwritingOffsetX,
                                protectedSize,
                                floatingMinY,
                            isHandwritingMode,
                            clipboardItemsState.value,
                            quickSendItemsState.value,
                            recentClipboardItemsState.value,
                            calculatorEngine.isActive(),
                            ) {
                                KeyboardUiState(
                                    clipboardImagesExpanded = imagePanelExpanded,
                                    rejectedCommitText = state.rejectedCommitText,
                                    isAsciiMode = state.isAsciiMode,
                                    schemaName = if (state.isAsciiMode) "英文" else state.schemaName,
                                    currentSchemaId = state.currentSchemaId,
                                    schemas = state.schemas,
                                    schemaSwitches = state.schemaSwitches,
                                    enterKeyText = state.enterKeyText,
                                    isDarkTheme = isDarkTheme,
                                    darkMode = state.darkMode,
                                    themeId = state.themeId,
                                    handwritingExpanded = handwritingExpanded,
                                    handwritingControlsWidthDp = protectedSize.width,
                                    handwritingControlsOffsetX = handwritingNormalX - handwritingOffsetX,
                                    keyboardHeightDp = effectiveKeyboardHeight,
                                    keyboardWidthDp = if (handwritingExpanded) handwritingWidth else protectedSize.width,
                                    keyboardBottomPaddingDp = contentBottomPaddingDp,
                                    keyboardOpacity = state.keyboardOpacity,
                                    // 词库部署/加载期间键盘区显示加载页（之前没传进来，加载页从不出现）。
                                    isDeploying = state.isDeploying,
                                    deploymentMessage = state.deploymentMessage,
                                    clipboardItems = clipboardItemsState.value,
                                    quickSendItems = quickSendItemsState.value,
                                    recentClipboardItems = recentClipboardItemsState.value,
                                    isVoiceMode = state.isVoiceMode,
                                    voiceSticky = state.voiceSticky,
                                    voiceBottomActive = state.voiceButtonState.bottomActive,
                                    voiceLeftActive = state.voiceButtonState.leftActive,
                                    voiceRightActive = state.voiceButtonState.rightActive,
                                    voicePluginName = state.voicePluginName,
                                    voiceRecognitionState = state.voiceRecognitionState,
                                    voiceRecognizedText = state.voiceRecognizedText,
                                    isSttEnabled = state.isSttEnabled,
                                    toolbarButtons = state.toolbarButtons,
                                    menuButtons = state.menuButtons,
                                    toolbarPluginButtons = state.toolbarPluginButtons,
                                    isCalculatorMode = calculatorEngine.isActive(),
                                    inputSessionId = state.inputSessionId,
                                    isFloatingMode = state.isFloatingMode && !handwritingExpanded,
                                    isHandwritingMode = isHandwritingMode,
                                    // Floating cards remain draggable even when an old size is being protected.
                                    floatingOffsetX = state.floatingOffsetX,
                                    floatingOffsetY = state.floatingOffsetY,
                                    floatingMinOffsetY = floatingMinY,
                                    floatingScreenHeightDp = effectiveScreenH,
                                    resizePreviewWidthDp = state.resizePreviewWidthDp,
                                    floatingWidthDp = state.floatingWidthDp,
                                    fixedWidthDp = if (handwritingExpanded) handwritingWidth else state.fixedWidthDp,
                                    fixedOffsetX = if (handwritingExpanded) handwritingOffsetX else protectedSize.offsetX,
                                    t9ResetSignal = state.t9ResetSignal,
                                    swipeCancelEpoch = state.swipeCancelEpoch,
                                    t9RightCandidateSelectedCount = state.t9RightCandidateSelectedCount,
                                    t9SelectedCandidatePinyin = state.t9SelectedCandidatePinyin,
                                    showQuickSendForm = state.showQuickSendForm,
                                    ocrDraft = state.ocrDraft,
                                    quickSendFormFocused = state.quickSendFormFocused,
                                    quickSendEditingItemId = state.quickSendEditingItemId,
                                    quickSendEditingItemText = state.quickSendEditingItemText,
                                    quickSendEditingItemCode = state.quickSendEditingItemCode,
                                    toolPanelVisible = state.toolPanelVisible,
                                    toolPanelInputFocused = state.toolPanelInputFocused,
                                    clipboardSearchVisible = state.clipboardSearchVisible,
                                    clipboardSearchResultsHeight = clipboardSearchResultsHeight,
                                    toolPanelPluginId = state.toolPanelPluginId,
                                    toolPanelTitle = state.toolPanelTitle,
                                    toolPanelPrefillText = state.toolPanelPrefillText,
                                    toolPanelItems = state.toolPanelItems,
                                    toolPanelLoading = state.toolPanelLoading,
                                    toolPanelRequestEpoch = state.toolPanelRequestEpoch,
                                    toolPanelDisplay = state.toolPanelDisplay,
                                    toolPanelUiNodes = state.toolPanelUiNodes,
                                    clipboardSyncEnabled = state.clipboardSyncEnabled,
                                )
                            }
                            val hapticView = LocalView.current
                            val resizeControls: (@Composable () -> Unit)? = if (state.showKeyboardResize) {
                                {
                              KeyboardResizeOverlay(
                                     initialHeightDp = effectiveKeyboardHeight,
                                     // 左右边/四角可调宽度：边界与屏宽一致，与几何纯函数同源。
                                     initialWidthDp = state.resizePreviewWidthDp,
                                     minWidthDp = com.kingzcheung.xime.ui.keyboard.keyboardWidthBounds(resources.configuration.screenWidthDp).first,
                                     maxWidthDp = com.kingzcheung.xime.ui.keyboard.keyboardWidthBounds(resources.configuration.screenWidthDp).last,
                                     defaultHeightDp = SettingsPreferences.getDefaultKeyboardHeightDp(this@XimeInputMethodService, screenIsLandscape),
                                     defaultSize = resetSize,
                                     currentBottomPaddingDp = contentBottomPaddingDp,
                                     isFloatingMode = state.isFloatingMode,
                                     initialOpacity = state.keyboardOpacity,
                                     onFloatingModeChange = { enabled ->
                                         restoreKeyboardDisplayMode(enabled, floatingMinY, persist = false)
                                     },
                                     isSplitKeyboard = SettingsPreferences.isSplitKeyboardEnabled(this@XimeInputMethodService),
                                     // 始终传入回调：当前方案不支持分体时由覆盖层禁用按钮，
                                     // 而不是让按钮整个消失（用户无法判断是禁用还是坏了）。
                                     splitKeyboardSupported = com.kingzcheung.xime.ui.keyboard.supportsSplitKeyboard(state.currentSchemaId, state.isAsciiMode),
                                     onSplitKeyboardChange = { enabled -> SettingsPreferences.setSplitKeyboardEnabled(this@XimeInputMethodService, enabled) },
                                     onPositionDrag = { dx, dy -> callbacks.onFloatingKeyboardDrag?.invoke(dx, dy) },
                                     onPositionDragEnd = { callbacks.onFloatingKeyboardDragEnd?.invoke() },
                                     onSnapFeedback = { feedbackManager.hapticFeedback(hapticView) },
                                     onOpacityChange = { opacity ->
                                         uiState.value = uiState.value.copy(keyboardOpacity = opacity)
                                     },
                                     onHeightChange = { newHeight ->
                                       uiState.value = uiState.value.copy(
                                           resizePreviewHeightDp = newHeight
                                       )
                                   },
                                   onGeometryChange = { geometry ->
                                       // 悬浮矩形拖动：一次写入宽度/高度/水平偏移/底边偏移，四个值同源。
                                       // 分体两半总是贴边占满宽度，不覆盖非分体时保存的宽度和水平位置。
                                       val split = splitKeyboardActive(uiState.value)
                                       if (uiState.value.isFloatingMode) uiState.value = uiState.value.copy(
                                           resizePreviewWidthDp = if (split) uiState.value.resizePreviewWidthDp else geometry.widthDp,
                                           resizePreviewHeightDp = geometry.heightDp,
                                           floatingOffsetX = if (split) uiState.value.floatingOffsetX else geometry.horizontalOffsetDp,
                                           floatingOffsetY = geometry.bottomOffsetDp,
                                       ) else if (!split) uiState.value = uiState.value.copy(
                                           fixedWidthDp = geometry.widthDp,
                                           fixedOffsetX = geometry.horizontalOffsetDp,
                                       )
                                   },

                                  onWidthChange = { newWidth ->
                                       // 宽度变了，水平可拖动范围跟着变：超出的偏移要收回，
                                           // 否则卡片会有一部分跑到屏幕外。
                                           val screenWidth = resources.configuration.screenWidthDp
                                           val halfMargin = ((screenWidth - newWidth) / 2f).roundToInt().coerceAtLeast(0)
                                           uiState.value = uiState.value.copy(
                                               resizePreviewWidthDp = newWidth,
                                               floatingOffsetX = uiState.value.floatingOffsetX.coerceIn(-halfMargin, halfMargin),
                                           )
                                   },
                                   onBottomPaddingChange = { newPadding ->
                                       uiState.value = uiState.value.copy(
                                           keyboardBottomPaddingDp = newPadding
                                       )
                                   },
                                  onReset = { defaultHeight ->
                                       uiState.value = uiState.value.copy(
                                           resizePreviewHeightDp = defaultHeight,
                                           keyboardBottomPaddingDp = 0,
                                           stretchFactor = 1f
                                       )
                                   },
                                  onConfirm = { newHeight, newPadding, floatingMode, opacity ->
                                       // 先快照：写偏好会触发监听器，不能再读被监听器重载过的预览偏移。
                                       val preview = uiState.value
                                       val savedHeight = KeyboardHeightProfiles.save(
                                           this@XimeInputMethodService, floatingMode, screenIsLandscape,
                                           newHeight, effectiveScreenH,
                                       )
                                       val savedWidth = preview.resizePreviewWidthDp
                                           .takeIf { floatingMode && it > 0 } ?: preview.floatingWidthDp
                                       if (floatingMode) {
                                           SettingsPreferences.setFloatingWidthDp(this@XimeInputMethodService, savedWidth, screenIsLandscape)
                                           SettingsPreferences.setFloatingOffsetX(this@XimeInputMethodService, preview.floatingOffsetX, screenIsLandscape)
                                           SettingsPreferences.setFloatingOffsetY(this@XimeInputMethodService, preview.floatingOffsetY, screenIsLandscape)
                                       }
                                       val savedPadding = if (floatingMode) {
                                           SettingsPreferences.getKeyboardBottomPaddingDp(this@XimeInputMethodService)
                                       } else newPadding
                                       if (!floatingMode) {
                                           SettingsPreferences.setKeyboardBottomPaddingDp(this@XimeInputMethodService, savedPadding)
                                           SettingsPreferences.setFixedHorizontalGeometry(this@XimeInputMethodService,
                                               preview.fixedWidthDp, preview.fixedOffsetX, screenIsLandscape)
                                       }
                                       SettingsPreferences.setKeyboardOpacity(this@XimeInputMethodService, opacity)
                                       SettingsPreferences.setFloatingMode(this@XimeInputMethodService, floatingMode, screenIsLandscape)
                                       uiState.value = uiState.value.copy(
                                           showKeyboardResize = false,
                                           isFloatingMode = floatingMode,
                                           keyboardHeightDp = savedHeight,
                                           resizePreviewHeightDp = 0,
                                           keyboardBottomPaddingDp = savedPadding,
                                           floatingWidthDp = savedWidth,
                                           fixedWidthDp = preview.fixedWidthDp,
                                           fixedOffsetX = preview.fixedOffsetX,
                                           resizePreviewWidthDp = 0,
                                           floatingOffsetX = preview.floatingOffsetX,
                                           floatingOffsetY = preview.floatingOffsetY,
                                           keyboardOpacity = opacity,
                                       )
                                       // 不再重复 toggleFloatingMode；它会重新读取设置并覆盖刚确认的预览位置。
                                       refreshKeyboardGeometry()
                                       applyWindowBackground()
                                    },
                                    onCancel = { cancelKeyboardResize() },
                                    modifier = Modifier
                                       .fillMaxSize()
                              )
                           }
                            } else null
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.kingzcheung.xime.ui.keyboard.LocalSplitKeyboardGap provides splitKeyboardGap) {
                            KeyboardView(
                                hardwareKeyboardConnected = hasHardwareKeyboard,
                                hardwareCandidateHighlight = if (hasHardwareKeyboard) keyRouter.hardwareCandidateSelection(
                                    cand, state.isAsciiMode || state.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.ENGLISH,
                                    predictionManager.hasPendingPrediction) else null,
                                inputSessionManagedByHost = true,
                                resizeOverlay = resizeControls,
                                fixedBottomInsetDp = activeBottomDp,
                                fixedHeightRange = heightBounds,
                                dockTarget = if (state.isFloatingMode && !state.showKeyboardResize && !handwritingExpanded)
                                    resolveKeyboardDockTarget(inputWindowWidthDp, state) else null,
                                viewModel = keyboardViewModel,
                                state = kbState,
                                candidateState = candidateState,
                                voiceAmplitudeState = this@XimeInputMethodService.voiceAmplitudeState,
                                voiceSpectrumState = this@XimeInputMethodService.voiceSpectrumState,
                                callbacks = callbacks,
                                inlineSuggestions = inlineSuggestionManager?.suggestions.orEmpty(),
                                // 悬浮透明度在卡片本身处理，宿主不再强制创建键盘大小的离屏缓冲。
                                modifier = if (state.isFloatingMode || state.showKeyboardResize) Modifier
                                    else Modifier.graphicsLayer {
                                        alpha = keyboardLayerOpacity
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    },
                                // 非按键交互（符号/表情面板、菜单栏、候选栏按钮）的振动，
                                // 语义与按键按下反馈完全一致（模式/时长/振幅走同一配置）
                                onHapticFeedback = { feedbackManager.hapticFeedback(hapticView) },
                                onCardPositioned = { left: Int, top: Int, right: Int, bottom: Int ->
                                    keyboardGlassCard = android.graphics.Rect(left, top, right, bottom)
                                    updateKeyboardGlass()
                                    val cardHeightPx = bottom - top
                                    if (!handwritingExpanded && !state.isFloatingMode && !state.showKeyboardResize && cardHeightPx > 0) {
                                        val bounds = android.graphics.Rect(left, top, right, bottom)
                                        if (fixedCardBounds != bounds) {
                                            fixedCardBounds = bounds
                                            keyboardContainer.requestLayout()
                                        }
                                    }
                                    if (!handwritingExpanded && state.isFloatingMode && !state.showKeyboardResize && cardHeightPx > 0) {
                                        currentEffectiveKeyboardHeight = (cardHeightPx / density.density).roundToInt()
                                        currentFloatingCardHeightDp = currentEffectiveKeyboardHeight
                                        currentFloatingCardWidthDp = ((right - left) / density.density).roundToInt()
                                        val bounds = android.graphics.Rect(left, top, right, bottom)
                                        if (floatingCardBounds != bounds) {
                                            floatingCardBounds = bounds
                                            keyboardContainer.requestLayout()
                                        }
                                    }
                                },
                            )
                            }
                           }

                           }
                            if (!state.isFloatingMode && navBarDp > 0.dp) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(navBarDp))
                            }
                       }
                      }
                 }
             }
         }
        
        }
        keyboardContainer.addView(composeView)

        applyWindowBackground()

        return keyboardContainer
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        win.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        try {
            window.window?.decorView
                ?.findViewById<FrameLayout>(android.R.id.inputArea)
                ?.let { area ->
                    area.updateLayoutParams<android.view.ViewGroup.LayoutParams> {
                        height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    // 栏外编码气泡越出容器顶边后进入 inputArea 空白区，
                    // inputArea 默认 clipChildren=true 会把越界部分裁掉。
                    area.clipChildren = false
                    // Only the keyboard card and navigation strip paint a background.
                    area.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            // 容器在 inputArea 内底部对齐（gravity BOTTOM）：
            // 容器物理高度 = Compose 内容总高，小于全屏窗口时若默认 top-left
            // 对齐会让键盘跑到屏顶。高度由 SideEffect 的 updateHeight 动态设置。
            view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                gravity = android.view.Gravity.BOTTOM
            }
            val state = uiState.value
            if (state.isFloatingMode || state.showKeyboardResize || state.isCompact) {
                view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                    height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                }
            } else {
                // 首显前预设容器高度：容器初始 MATCH_PARENT（贴底 → 顶部 y=0）会让窗口
                // 第一次 traversal 的 onComputeInsets 报告"键盘占满全屏"，而 SideEffect 的
                // updateHeight 下一帧才生效；部分应用按首次 inset 布局后不再响应修正，
                // 输入框被顶到屏顶、与键盘间留大片空白（重进时容器已带正确高度故不复现）。
                // 按偏好高度预设首帧真实几何，之后仍由 SideEffect 统一维护。
                val density = resources.displayMetrics.density
                val activeBottomDp = imeBottomSpaceDp(bottomInsetPxState.value, density)
                val displayHeight = resolveKeyboardSize(resources.configuration.screenWidthDp,
                    resources.configuration.screenHeightDp, state.keyboardBottomPaddingDp + activeBottomDp, state).height
                view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                    height = ((displayHeight + state.keyboardBottomPaddingDp + activeBottomDp) * density).toInt()
                }
            }
        } catch (_: Exception) {}
    }
    
    // ── ActionExecutor 实现 ──

    override fun performEditorMenuAction(actionId: Int) {
        when (actionId) {
            android.R.id.undo, android.R.id.redo -> {
                // performContextMenuAction 对 undo 支持不一致，改用 Ctrl+Z 键盘快捷键
                val now = SystemClock.uptimeMillis()
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or if (actionId == android.R.id.redo) KeyEvent.META_SHIFT_ON else 0)
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or if (actionId == android.R.id.redo) KeyEvent.META_SHIFT_ON else 0)
                )
            }
            else -> currentInputConnection?.performContextMenuAction(actionId)
        }
    }

    /** Shared by Compose, first-frame insets, resize entry/reset and mode changes. */
    internal fun resolveKeyboardSize(
        availableWidthDp: Int,
        hostHeightDp: Int,
        bottomSpaceDp: Int,
        state: InputUIState = uiState.value,
        floating: Boolean = state.isFloatingMode,
        reset: Boolean = false,
    ): ProtectedKeyboardSize {
        val config = resources.configuration
        val landscape = config.screenWidthDp > config.screenHeightDp
        val preview = state.showKeyboardResize && !reset
        val height = if (preview && state.resizePreviewHeightDp > 0) state.resizePreviewHeightDp else
            if (reset) SettingsPreferences.getDefaultKeyboardHeightDp(this, landscape)
            else KeyboardHeightProfiles.selected(this, floating, landscape, hostHeightDp)
        // 悬浮：已存或预览中的高度可以低到方形吸附点（默认高度已在 KeyboardHeightProfiles 里按默认下限夹过）。
        val heightBounds = if (floating)
            com.kingzcheung.xime.ui.keyboard.floatingStoredHeightBounds(hostHeightDp, landscape)
            else KeyboardHeightProfiles.fixedBounds(this, landscape)
        val savedWidth = if (reset) 0 else if (floating)
            (state.resizePreviewWidthDp.takeIf { preview && it > 0 } ?: state.floatingWidthDp)
            else state.fixedWidthDp
        val width = savedWidth.takeIf { it > 0 } ?: if (floating)
            com.kingzcheung.xime.ui.keyboard.resolvedFloatingWidth(
                availableWidthDp, config.screenHeightDp, height, maxOf(config.screenWidthDp, config.screenHeightDp),
                state.inputProfile.layout.kind in setOf(com.kingzcheung.xime.settings.LayoutKind.ALPHABETIC,
                    com.kingzcheung.xime.settings.LayoutKind.MERGED), 0,
            ) else availableWidthDp
        // Fixed mode's configuration height already describes its usable viewport.
        // Resize overlays use a taller host, but must not change the default budget.
        val availableHeight = (if (floating) hostHeightDp - bottomSpaceDp - FLOATING_DRAG_BAR_HEIGHT_DP
            else config.screenHeightDp - (if (reset) 0 else state.keyboardBottomPaddingDp)).coerceAtLeast(1)
        val maximumHeight = heightBounds.last.coerceAtMost(availableHeight)
        val requestedHeight = height.coerceIn(heightBounds.first.coerceAtMost(maximumHeight)..maximumHeight)
        val split = splitKeyboardActive(state)
        val physicalMetrics = keyboardPhysicalMetrics(this, window.window?.decorView?.display)
        val splitGap = if (split) com.kingzcheung.xime.ui.keyboard.SplitKeyboardGeometry.resolvedGap(
            SettingsPreferences.getSplitGapFraction(this, landscape), availableWidthDp.toFloat(),
            com.kingzcheung.xime.ui.keyboard.SplitKeyboardGeometry.gutterDp(floating),
            physicalMetrics?.dpFromMillimeters(com.kingzcheung.xime.ui.keyboard.CALIBRATED_QWERTY_PITCH_MM)) else 0.1f
        val protected = protectKeyboardSize(
            availableWidthDp, if (split) availableWidthDp else width, requestedHeight,
            if (reset) 0 else if (floating) state.floatingOffsetX else state.fixedOffsetX,
            KeyboardAspectLimits.forLayout(state.inputProfile.layout.kind, split, splitGap),
            customSize = !reset && (preview || savedWidth > 0 || KeyboardHeightProfiles.hasSavedHeight(this, floating, landscape)),
            letterDefaults = letterKeyboardDefaults(state.inputProfile, availableHeight, state.isAsciiMode, floating,
                split = split,
                physicalMetrics = physicalMetrics,
                maximumHeightDp = maximumHeight),
        )
        // 分体两半贴住左右边：宽度占满、无水平偏移；保存的非分体宽度不受影响。
        return if (split) protected.copy(width = availableWidthDp, offsetX = 0) else protected
    }

    /** Both the restore operation and its preview resolve the saved fixed geometry here. */
    private fun resolveFixedKeyboardRestore(
        availableWidthDp: Int, state: InputUIState,
    ): Pair<InputUIState, ProtectedKeyboardSize> {
        val config = resources.configuration
        val landscape = config.screenWidthDp > config.screenHeightDp
        val fixed = state.copy(showKeyboardResize = false, isFloatingMode = false,
            fixedWidthDp = SettingsPreferences.getFixedWidthDp(this, landscape),
            fixedOffsetX = SettingsPreferences.getFixedOffsetX(this, landscape))
        val bottom = imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density)
        return fixed to resolveKeyboardSize(availableWidthDp, config.screenHeightDp,
            fixed.keyboardBottomPaddingDp + bottom, fixed, floating = false)
    }

    /** Destination after docking settles, including tools that remain open in fixed mode. */
    internal fun resolveKeyboardDockTarget(
        availableWidthDp: Int, state: InputUIState = uiState.value,
    ): KeyboardDockTarget {
        val (fixed, normal) = resolveFixedKeyboardRestore(availableWidthDp, state)
        val page = keyboardViewModel.page.value
        val bottom = imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density)
        val clipboardBottom = maxOf(bottom, tryGetVisibleNavBarHeightDp(this, window.window)) +
            fixed.keyboardBottomPaddingDp
        val contentHeight = if (clipboardPanelCanExpand(page, false, fixed.isCompact) && fixed.clipboardImagesExpanded)
            clipboardPanelBounds(normal.height, resources.configuration.screenHeightDp, clipboardBottom).expanded
            else normal.height
        val toolExtra = if (fixed.toolPanelVisible && fixed.toolPanelDisplay != "PASSIVE" &&
            page !is KeyboardPage.Overlay) 170 else 0
        return KeyboardDockTarget(normal.width, contentHeight + toolExtra, normal.offsetX,
            fixed.keyboardBottomPaddingDp, bottom)
    }

    /** 读取目标模式的尺寸；恢复固定时透明度归零，保留各模式的位置。 */
    internal fun restoreKeyboardDisplayMode(enabled: Boolean, navBarDp: Int = 0, persist: Boolean = true) {
        val config = resources.configuration
        val landscape = config.screenWidthDp > config.screenHeightDp
        val before = uiState.value
        val restoringFixed = before.isFloatingMode && !enabled
        val hostHeight = ((resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt() -
            tryGetStatusBarHeightDp(this, window.window)).coerceAtLeast(1)
        val savedWidth = SettingsPreferences.getFloatingWidthDp(this, landscape)
        val savedFixedWidth = SettingsPreferences.getFixedWidthDp(this, landscape)
        val savedX = SettingsPreferences.getFloatingOffsetX(this, landscape)
        val savedY = SettingsPreferences.getFloatingOffsetY(this, landscape)
        val availableWidth = if (::keyboardContainer.isInitialized)
            (keyboardContainer.width / resources.displayMetrics.density).roundToInt().takeIf { it > 0 }
                ?: config.screenWidthDp else config.screenWidthDp
        val fixedRestore = if (!enabled) resolveFixedKeyboardRestore(availableWidth, before) else null
        val targetSize = fixedRestore?.second ?: resolveKeyboardSize(availableWidth,
            if (enabled || before.showKeyboardResize) hostHeight else config.screenHeightDp,
            if (enabled) maxOf(navBarDp, tryGetVisibleNavBarHeightDp(this, window.window),
                kotlin.math.ceil(bottomInsetPxState.value / resources.displayMetrics.density).toInt())
            else before.keyboardBottomPaddingDp +
                imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density),
            before.copy(showKeyboardResize = false, floatingWidthDp = savedWidth, fixedWidthDp = savedFixedWidth),
            floating = enabled)
        val targetHeight = targetSize.height
        if (persist) {
            if (restoringFixed) SettingsPreferences.setKeyboardOpacity(this, 1f)
            SettingsPreferences.setFloatingMode(this, enabled, landscape)
        }
        uiState.value = uiState.value.copy(
            isFloatingMode = enabled,
            keyboardOpacity = if (restoringFixed) 1f else before.keyboardOpacity,
            keyboardHeightDp = targetHeight,
            resizePreviewHeightDp = if (before.showKeyboardResize) targetHeight else 0,
            resizePreviewWidthDp = if (before.showKeyboardResize && enabled) targetSize.width else 0,
            floatingWidthDp = savedWidth,
            fixedWidthDp = if (before.showKeyboardResize && !enabled) targetSize.width
                else fixedRestore?.first?.fixedWidthDp ?: savedFixedWidth,
            fixedOffsetX = fixedRestore?.first?.fixedOffsetX ?: SettingsPreferences.getFixedOffsetX(this, landscape),
            floatingOffsetX = savedX,
            floatingOffsetY = savedY,
        )
        if (enabled) closeToolPanel()
        refreshKeyboardGeometry()
        applyWindowBackground()
    }

    internal fun cancelKeyboardResize() {
        if (!uiState.value.showKeyboardResize) return
        SettingsPreferences.setSplitKeyboardEnabled(this, uiState.value.resizeInitialSplit)
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        uiState.value = uiState.value.copy(
            showKeyboardResize = false,
            isFloatingMode = uiState.value.resizeInitialFloating,
            floatingOffsetX = uiState.value.resizeInitialX,
            floatingOffsetY = uiState.value.resizeInitialY,
            fixedWidthDp = SettingsPreferences.getFixedWidthDp(this, isLandscape),
            fixedOffsetX = SettingsPreferences.getFixedOffsetX(this, isLandscape),
            resizePreviewWidthDp = 0,
            resizePreviewHeightDp = 0,
            keyboardHeightDp = KeyboardHeightProfiles.selected(
                this, uiState.value.resizeInitialFloating, isLandscape,
                ((resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt() -
                    tryGetStatusBarHeightDp(this, window.window)).coerceAtLeast(1),
            ),
            keyboardBottomPaddingDp = SettingsPreferences.getKeyboardBottomPaddingDp(this),
            keyboardOpacity = SettingsPreferences.getKeyboardOpacity(this),
        )
        refreshKeyboardGeometry()
    }

    /** 模式变化立即撤销旧卡片的高度/触摸区域，避免宿主缓存上一帧的浮动留白。 */
    internal fun refreshKeyboardGeometry() {
        floatingCardBounds = null
        fixedCardBounds = null
        clearHardwareSurfaceBounds()
        currentResolvedKeyboardSize = null
        currentFloatingCardHeightDp = 0
        currentEffectiveKeyboardHeight = 0
        if (::keyboardContainer.isInitialized) {
            val state = uiState.value
            val config = resources.configuration
            val screenHeight = config.screenHeightDp
            val activeBottom = imeBottomSpaceDp(bottomInsetPxState.value, resources.displayMetrics.density)
            // 悬浮模式必须让输入法宿主保持“物理可见全高”。
            // 不能在确认后又退回 configuration.screenHeightDp；那会把卡片上半部裁出 IME 宿主。
            val physicalScreenDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt()
            val statusBarDp = tryGetStatusBarHeightDp(this, window.window)
            val floatingHostHeight = (physicalScreenDp - statusBarDp).coerceAtLeast(1)
            val height = when {
                state.isFloatingMode || state.showKeyboardResize || state.isCompact -> floatingHostHeight
                else -> {
                    val content = resolveKeyboardSize(config.screenWidthDp, screenHeight,
                        state.keyboardBottomPaddingDp + activeBottom, state).height
                    content + state.keyboardBottomPaddingDp + activeBottom
                }
            }
            keyboardContainer.updateHeight(height)
            keyboardContainer.requestLayout()
        }
        window.window?.decorView?.requestApplyInsets()
    }

    private val hardwareLanguageShortcuts = HardwareLanguageShortcuts()
    private var clipboardRestoreCompact = false
    private var microphoneRestoreCompact = false
    private val clipboardNavigationKeys = mutableSetOf<Int>()
    private val hardwareCandidateNavigationKeys = mutableSetOf<Int>()
    /** Shift 加数字或空格已用来选候选（上屏英文）的键，松开时一并吞掉。 */
    private val glossShiftKeys = mutableSetOf<Int>()
    private val clipboardKeyboard = com.kingzcheung.xime.clipboard.ClipboardKeyboardNavigation {
        keyboardViewModel.leaveClipboard()
        if (clipboardRestoreCompact) collapseScreenKeyboardToHardwareToolbar()
        clipboardRestoreCompact = false
    }

    private fun handleHardwareTool(tool: HardwareToolShortcut) {
        when (tool) {
            HardwareToolShortcut.PUNCTUATION -> {
                if (inputReadiness.ticket() != null) sessionController.togglePunctuationWidth(notifyAscii = true)
            }
            HardwareToolShortcut.CLIPBOARD -> {
                val route = (keyboardViewModel.page.value as? KeyboardPage.Overlay)?.route
                if (route is OverlayRoute.Clipboard) clipboardKeyboard.close()
                else {
                    clipboardRestoreCompact = uiState.value.isCompact
                    clipboardKeyboard.open()
                    showScreenKeyboardFromHardware(OverlayRoute.Clipboard())
                }
            }
            HardwareToolShortcut.MICROPHONE -> {
                if (uiState.value.isVoiceMode) {
                    endVoiceSession()
                    if (microphoneRestoreCompact) collapseScreenKeyboardToHardwareToolbar()
                    microphoneRestoreCompact = false
                } else if (!uiState.value.isSttEnabled) {
                    android.widget.Toast.makeText(this, "请先在设置中启用语音输入", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    microphoneRestoreCompact = uiState.value.isCompact
                    if (microphoneRestoreCompact) showScreenKeyboardFromHardware()
                    keyboardCallbacks?.onVoiceStickyToggle?.invoke()
                }
            }
        }
    }

    private var hardwareCursorImmediateOnly = false
    private fun switchHardwareLanguage(mode: com.kingzcheung.xime.settings.LanguageSwitchMode? = null) {
        keyboardViewModel.resetShift()
        keyRouter.handleKeyPress("ime_switch", uiState.value.isAsciiMode, languageSwitchMode = mode)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let { hardwareCapsLockState.value = it.isCapsLockOn }
        if (hasHardwareKeyboard) event?.let { feedbackManager.playHardwareKeySound(it) }
        cancelVerificationCodeInput()
        if (uiState.value.showQuickSendForm && keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            keyRouter.handleKeyPress("enter", event?.isShiftPressed == true)
            return true
        }
        if (uiState.value.showQuickSendForm && keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)) {
            event?.startTracking()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            (keyboardViewModel.page.value is KeyboardPage.Overlay || keyboardCallbacks?.onDismissPreeditEditor != null ||
                uiState.value.showKeyboardResize || uiState.value.handwritingExpanded || uiState.value.clipboardSearchVisible ||
                (hasHardwareKeyboard && hardwareKeyboardDisplay.screenKeyboardRequested) || canCollapseToVoicePill())) {
            event?.startTracking()
            return true
        }
        val e = event ?: return super.onKeyDown(keyCode, event)
        if (hasHardwareKeyboard) {
            // Shift 单击切语言时这个键会被下面的快捷键吞掉，按住状态要先记下。
            if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                com.kingzcheung.xime.gloss.CandidateGlossary.physicalShiftHeld.value = true
            }
            val shortcut = hardwareLanguageShortcuts.down(keyCode, e.eventTime, e.repeatCount,
                e.isCtrlPressed, e.isAltPressed, e.isMetaPressed, e.isShiftPressed, uiState.value.hardwareOptions)
            shortcut.switchMode?.let { switchHardwareLanguage(it) }
            shortcut.tool?.let { handleHardwareTool(it) }
            if (shortcut.consume) return true
            val continuingCandidatePress = e.repeatCount > 0 && hardwareCandidateNavigationKeys.contains(keyCode)
            if ((e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) && !continuingCandidatePress && !isHardwareArrow(keyCode)) {
                return super.onKeyDown(keyCode, event)
            }
            // 按住 Shift：候选栏中英对调；Shift 加数字或空格选候选时上屏英文（只在释义词典生效时接管）。
            if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT &&
                e.isShiftPressed && e.repeatCount == 0 && keyRouter.selectHardwareCandidateWithShift(keyCode)) {
                glossShiftKeys += keyCode
                return true
            }
            val clipboardRoute = (keyboardViewModel.page.value as? KeyboardPage.Overlay)?.route as? OverlayRoute.Clipboard
            val clipboardOpen = clipboardRoute != null
            if (clipboardKeyboard.active && !clipboardOpen) clipboardKeyboard.reset()
            if (clipboardNavigationKeys.contains(keyCode) && e.repeatCount > 0 && !clipboardOpen) return true
            if (clipboardOpen && clipboardKeyboard.active) {
                val action = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> com.kingzcheung.xime.clipboard.ClipboardNavigation.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> com.kingzcheung.xime.clipboard.ClipboardNavigation.RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> com.kingzcheung.xime.clipboard.ClipboardNavigation.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> com.kingzcheung.xime.clipboard.ClipboardNavigation.DOWN
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> com.kingzcheung.xime.clipboard.ClipboardNavigation.CONFIRM
                    KeyEvent.KEYCODE_ESCAPE -> com.kingzcheung.xime.clipboard.ClipboardNavigation.CANCEL
                    KeyEvent.KEYCODE_SPACE -> com.kingzcheung.xime.clipboard.ClipboardNavigation.MENU
                    KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL -> com.kingzcheung.xime.clipboard.ClipboardNavigation.DELETE
                    else -> null
                }
                if (action != null) {
                    clipboardNavigationKeys += keyCode
                    if (e.repeatCount == 0 || action !in listOf(com.kingzcheung.xime.clipboard.ClipboardNavigation.CONFIRM,
                            com.kingzcheung.xime.clipboard.ClipboardNavigation.CANCEL, com.kingzcheung.xime.clipboard.ClipboardNavigation.MENU,
                            com.kingzcheung.xime.clipboard.ClipboardNavigation.DELETE)) clipboardKeyboard.dispatch(action)
                    return true
                }
            }
            // Back to the physical keyboard after expanding the on-screen one: collapse it to the
            // toolbar on the first typed character, so it stops covering the app.
            if (hardwareKeyboardDisplay.screenKeyboardRequested && !clipboardOpen &&
                keyCodeToKey(keyCode, e.isShiftPressed) != null && keyCode != KeyEvent.KEYCODE_SPACE) {
                collapseScreenKeyboardToHardwareToolbar()
            }
            // Typing after a dismissed input view must restore its surface without
            // requiring the user to refocus the editor. Esc itself stays inside IME.
            if (!isInputViewShown && currentInputConnection != null &&
                (keyCodeToKey(keyCode, e.isShiftPressed) != null || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                requestShowSelf(0)
            }
            // Resolve cursor/candidate actions after earlier letters in the same FIFO.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                hardwareCandidateDigitIndex(keyCode, e.isShiftPressed) != null) {
                if (keyRouter.handleHardwareCandidateKey(keyCode, e)) {
                    hardwareCandidateNavigationKeys += keyCode
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
        }
        val pagingKey = hardwareCandidatePageDirection(keyCode, hasInput = true,
            modified = e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) != 0
        if (hasHardwareKeyboard && pagingKey) {
            val symbol = keyCodeToKey(keyCode, e.isShiftPressed)
            if (symbol != null) {
                val pageDirection = hardwareCandidatePageDirection(keyCode, hasInput = true,
                    modified = false, options = uiState.value.hardwareOptions)
                if (pageDirection == 0) {
                    // Rime also binds -= and [] to paging. An explicitly disabled group
                    // must use the ordinary symbol-key path, not reach that second binding.
                    if (inputReadiness.ticket() != null && !keyRouter.hasPendingCandidateCommit) {
                        textCommit.commitLiteralText(symbol, hardware = true)
                    }
                } else {
                    // Check composition inside the FIFO queue, after preceding letters/commits.
                    // UI candidates may still be empty during rapid physical input.
                    keyRouter.handleKeyPress(symbol, e.isShiftPressed, hardwarePageDirection = pageDirection)
                }
                return true
            }
        }
        val chinese = uiState.value.inputProfile.language == com.kingzcheung.xime.settings.InputLanguage.CHINESE
        // Caps Lock while composing commits the letters as typed (as on the desktop); the system
        // still toggles the lock, so the key is not consumed.
        if (keyCode == KeyEvent.KEYCODE_CAPS_LOCK && hasHardwareKeyboard && e.repeatCount == 0 &&
            chinese && !uiState.value.isAsciiMode) keyRouter.commitCompositionAsTyped()
        // Caps Lock inverts letter case like Shift (digits and symbols keep their own Shift meaning);
        // a capital letter in Chinese is typed literally. Japanese kana input keeps its own case rules.
        val capsLetter = e.isCapsLockOn && keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z &&
            uiState.value.inputProfile.language != com.kingzcheung.xime.settings.InputLanguage.JAPANESE
        val isShifted = e.isShiftPressed != capsLetter
        val key = keyCodeToKey(keyCode, isShifted)
        if (key != null) {
            keyRouter.handleKeyPress(key, isShifted, hardwareEvent = if (hasHardwareKeyboard) KeyEvent(e) else null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let { hardwareCapsLockState.value = it.isCapsLockOn }
        if (uiState.value.showQuickSendForm && keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) return true
        if (uiState.value.showQuickSendForm && keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)) {
            if (event?.isCanceled != true) keyboardCallbacks?.onHideQuickSendForm?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            com.kingzcheung.xime.gloss.CandidateGlossary.physicalShiftHeld.value = event?.isShiftPressed == true
        }
        if (glossShiftKeys.remove(keyCode)) return true
        val clipboardConsumed = clipboardNavigationKeys.remove(keyCode)
        val candidateConsumed = hardwareCandidateNavigationKeys.remove(keyCode)
        if (candidateConsumed) keyRouter.releaseHardwareCandidateKey(keyCode)
        if (hasHardwareKeyboard && event != null) {
            val shortcut = hardwareLanguageShortcuts.up(keyCode, event.eventTime, event.isCanceled, uiState.value.hardwareOptions)
            shortcut.switchMode?.let { switchHardwareLanguage(it) }
            if (shortcut.consume || clipboardConsumed || candidateConsumed) return true
            if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) return true
        }
        if (clipboardConsumed || candidateConsumed) return true
        // Back leaves clipboard search the way its X does: back to the clipboard, keyboard kept.
        if (keyCode == KeyEvent.KEYCODE_BACK && uiState.value.clipboardSearchVisible) {
            if (event?.isCanceled != true) closeClipboardSearch(openBoard = true)
            return true
        }
        val overlay = keyboardViewModel.page.value as? KeyboardPage.Overlay
        if (keyCode == KeyEvent.KEYCODE_BACK && overlay != null) {
            if (event?.isCanceled != true) {
                if (overlay.backStack.isEmpty()) keyboardViewModel.closeOverlay()
                else keyboardViewModel.popOverlay()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && uiState.value.handwritingExpanded) {
            if (event?.isCanceled != true) uiState.value = uiState.value.copy(handwritingExpanded = false)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && keyboardCallbacks?.onDismissPreeditEditor != null) {
            if (event?.isCanceled != true) keyboardCallbacks?.onDismissPreeditEditor?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && uiState.value.showKeyboardResize) {
            if (event?.isCanceled != true) cancelKeyboardResize()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && hasHardwareKeyboard && hardwareKeyboardDisplay.screenKeyboardRequested) {
            if (event?.isCanceled != true) collapseScreenKeyboardToHardwareToolbar()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && canCollapseToVoicePill()) {
            if (event?.isCanceled != true) collapseToVoicePill()
            return true
        }
        // 悬浮条上再按返回是有意关闭：不再自动弹回。
        if (keyCode == KeyEvent.KEYCODE_BACK && uiState.value.voiceCollapsed && event?.isCanceled != true) voicePillDismissed = true
        return super.onKeyUp(keyCode, event)
    }

    override fun sendKeyEvent(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            schemaController.moveEditorCursorVertical(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
            return
        }
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun executeCommand(name: String) {
        when (name) {
            "clear_composition" -> {
                keyRouter.postRimeJob {
                    rimeEngine.clearComposition()
                    withContext(Dispatchers.Main) {
                        mainHandler.post { updateUI() }
                    }
                }
            }
            "show_ime_picker" -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                @Suppress("DEPRECATION")
                imm.showInputMethodPicker()
            }
            else -> FileLogger.w(TAG, "Unknown command: $name")
        }
    }

    override fun repeatLastInput() {
        val lastText = predictionManager.lastCommittedText
        if (lastText.isNotEmpty()) {
            currentInputConnection?.commitText(lastText, 1)
        }
    }

    override fun dispatchKey(key: String) {
        // 与物理键盘 onKeyDown 同一入口：handleKeyPress 内部自行调度到 key-processing 线程
        keyRouter.handleKeyPress(key, false)
    }

    // ── 原有方法 ──

    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // 新连接开始时旧组合区一律失效；同一输入框重启时撤掉被定格的拼音编码。
        val identity = attribute?.let { Triple(it.packageName, it.fieldId, it.inputType) }
        val sameField = restarting && identity != null && identity == lastEditorIdentity
        lastEditorIdentity = identity
        inlineComposition.detach()?.let { removeDetachedComposition(it, attribute, restarting, sameField) }
        englishEditorReadCapability.clear()
        if (inputStallWatchdog == null) inputStallWatchdog = InputStallWatchdog(serviceScope, mainHandler) { keyProcessingThread }
        inputStallWatchdog?.start()
        hardwareLanguageShortcuts.reset()
        clipboardKeyboard.reset()
        clipboardNavigationKeys.clear()
        hardwareCandidateNavigationKeys.clear()
        glossShiftKeys.clear()
        com.kingzcheung.xime.gloss.CandidateGlossary.physicalShiftHeld.value = false
        com.kingzcheung.xime.gloss.CandidateGlossary.t9NextHeld.value = false
        clipboardRestoreCompact = false
        microphoneRestoreCompact = false
        uiState.value = uiState.value.copy(hardwareOptions = com.kingzcheung.xime.settings.HardwareKeyboardPreferences.read(this))
        // A hardware keyboard can switch editors without starting the soft input view.
        // Never keep the previous editor's position while waiting for this connection.
        uiState.value = uiState.value.copy(cursorAnchor = null, editorBounds = null)
        updateHardwareKeyboardConnection()
        applyCompactMode()
        requestHardwareCursorUpdates()
        inputReadiness.newEditorSession()
        keyRouter.discardPendingCandidateCommit()
        voiceRecognitionHandler.endInputSession()
        if (uiState.value.isVoiceMode || voiceRecordingStarted) {
            voiceRecognitionHandler.abandonSession()
            voiceRecognitionHandler.cancelPreStart()
            restoreAfterVoiceFinish()
        }
        schemaController.resetEditorSelection()
        schemaController.observeEditorSelection(attribute?.initialSelStart ?: -1, attribute?.initialSelEnd ?: -1)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        loadDarkModePreference()

        // 敏感输入框（密码等）判定 + composing 去重标志重置（详见 PluginEventDispatcher）
        pluginEvents.onStartInput(attribute)
        com.kingzcheung.xime.settings.IncognitoState.sensitiveEditor =
            com.kingzcheung.xime.settings.IncognitoState.isSensitiveEditor(attribute)
        applyIncognito()

        // 受限输入框判定（密码/终端/NO_SUGGESTIONS）：供英文联想等补全功能短路
        editorRestricted = EditorInfoClassifier.isRestrictedEditor(attribute)
        // 秘密输入框判定（密码/终端，不含 NO_SUGGESTIONS）：英文联想/回删替换的禁用线
        editorSecret = EditorInfoClassifier.isSecretEditor(attribute)
        val editorPresentation = editorInputPolicy.begin(attribute, restarting,
            uiState.value.isAsciiMode, uiState.value.currentSchemaId)
        lastClearedText = ""

        // 旧组合区已在 onStartInput 开头脱离（重启输入时框架也会定格它），此处不再保留标记，
        // 避免 endComposingInputBox 对新连接执行 setComposingText("")（选中文字时等于删除选区）。

        predictionManager.clearCommittedText()
        // 新输入会话清空 partial commit 累积：外部 UI（如设置页输入框"清除"按钮仅清 Compose
        // state）会触发 restartInput → 此处重建 T9，若残留累积会被 buildT9DisplayState 拼进
        // preedit 回灌输入框（2026-08-07 日志实证：清除后 testText 从 '' 回灌为 '几乎'）。
        t9PartialSegments.clear()
        debugLog("onStartInput: cleared lastCommittedText restarting=$restarting sameField=$sameField page=${keyboardViewModel.page.value::class.simpleName}")

        // 跨进程同步文件日志开关（开关在主进程设置页切换）
        FileLogger.setVerboseLoggingEnabled(
            SettingsPreferences.isVerboseLoggingEnabled(this)
        )
        
        uiState.value = uiState.value.copy(
            inputSessionId = System.nanoTime(),
            isAsciiMode = editorPresentation.isAsciiMode,
            currentSchemaId = editorPresentation.currentSchemaId,
            clipboardImagesExpanded = false,
            isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService),
        )

        // Show the existing layout immediately. Native preparation is queued below,
        // before the first key, rather than loading translators on this callback.
        if (!restarting) keyboardViewModel.resetKeyboard(uiState.value.isAsciiMode,
            uiState.value.currentSchemaId.ifBlank { SettingsPreferences.getCurrentSchema(this) },
            editorInputPolicy.field.numberPanel ||
                (EditorInfoClassifier.isNumberEditor(attribute) && SettingsPreferences.isAutoNumberKeyboardEnabled(this)))
        synchronizeKeyboardInputSession()

        // 先重置候选状态到初始值，避免前一 session 的残留状态影响新输入
        candidateState.value = CandidateState()

        // 获取最近30秒的剪切板内容
        ensureClipboardManagerInitialized()
        try {
            if (clipboardManager.images.pasteFailure.value?.packageName != attribute?.packageName) clipboardManager.images.clearPasteFailure()
            clipboardManager.refreshClipboard()
            recentClipboardItemsState.value = clipboardManager.getRecentItems(30)
            // 将最近剪切板内容显示在候选栏
            candidateState.value = candidateState.value.copy(
                candidates = recentClipboardItemsState.value.map { it.text },
                clipboardImage = clipboardManager.images.preview.value?.takeIf { it.timestamp >= (recentClipboardItemsState.value.maxOfOrNull { item -> item.timestamp } ?: 0) },
                smsVerificationCode = clipboardManager.verificationCode.value?.text,
                candidateComments = emptyList(),
                isShowingRecentClipboard = true
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to get recent clipboard items", e)
        }

        // 监听clipboardItems变化，更新候选栏
        clipboardCollectorJob?.cancel()
        clipboardCollectorJob = serviceScope.launch {
            combine(clipboardManager.clipboardItems, clipboardManager.verificationCode, clipboardManager.images.preview) { _, _, _ -> Unit }.collect {
                val items = clipboardManager.getRecentItems(30)
                if (!canShowClipboardSuggestions(candidateState.value.isComposing,
                        candidateState.value.pendingEnglishText, inputCommands.hasPendingCommands)) return@collect
                recentClipboardItemsState.value = items
                val image = clipboardManager.images.preview.value?.takeIf { it.timestamp >= (items.maxOfOrNull { item -> item.timestamp } ?: 0) }
                if (items.isNotEmpty() || image != null) {
                    // 清空Rime联想词等
                    rimeEngine.clearComposition()
                    candidateState.value = candidateState.value.copy(
                        candidates = items.map { it.text },
                        clipboardImage = image,
                        smsVerificationCode = clipboardManager.verificationCode.value?.text,
                        candidateComments = emptyList(),
                        inputText = "",
                        isComposing = false,
                        associationCandidates = emptyList(),
                        isShowingRecentClipboard = true
                    )
                } else if (candidateState.value.isShowingRecentClipboard) {
                    // 如果没有recent items，清空候选栏
                    candidateState.value = candidateState.value.copy(
                        candidates = emptyList(),
                        clipboardImage = null,
                        candidateComments = emptyList(),
                        isShowingRecentClipboard = false,
                        candidateActions = emptyList()
                    )
                }
            }
        }

        EditorInputPreparation(this).schedule(restarting, EditorInfoClassifier.isNumberEditor(attribute))
        attribute?.let { updateEnterKeyText(it) }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        startRuntimeObservation()
        info?.let { updateEnterKeyText(it) }
        updateHardwareKeyboardConnection()
        applyCompactMode()
        applyWindowBackground()
        requestHardwareCursorUpdates()
        openPendingOcrPanel()
        // 释义词典随设置与下载状态后台加载/释放；选拼音时不占内存。
        com.kingzcheung.xime.gloss.CandidateGlossary.prepare(this)
    }

    private fun requestHardwareCursorUpdates() {
        hardwareCursorImmediateOnly = false
        val connection = currentInputConnection ?: return
        if (!hasHardwareKeyboard) {
            try { connection.requestCursorUpdates(0) } catch (_: Exception) { }
            return
        }
        val mode = InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE
        fun request(flags: Int, filtered: Boolean = false): Boolean = try {
            if (filtered && Build.VERSION.SDK_INT >= 33) connection.requestCursorUpdates(flags,
                InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER or InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS or
                    InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS)
            else connection.requestCursorUpdates(flags)
        } catch (_: Exception) { false }
        val acceptedMode = when {
            Build.VERSION.SDK_INT >= 33 && request(mode, filtered = true) -> mode
            request(mode) -> mode
            request(InputConnection.CURSOR_UPDATE_MONITOR) -> InputConnection.CURSOR_UPDATE_MONITOR
            request(InputConnection.CURSOR_UPDATE_IMMEDIATE) -> InputConnection.CURSOR_UPDATE_IMMEDIATE
            else -> 0
        }
        hardwareCursorImmediateOnly = acceptedMode == InputConnection.CURSOR_UPDATE_IMMEDIATE
        if (acceptedMode == 0) uiState.value = uiState.value.copy(cursorAnchor = null, editorBounds = null)
        debugLog("Hardware cursor request package=${currentInputEditorInfo?.packageName} mode=$acceptedMode")
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        schemaController.observeEditorSelection(newSelStart, newSelEnd)
        val current = candidateState.value
        if (!current.isComposing && current.inputText.isEmpty() &&
            (predictionManager.hasPendingPrediction || current.associationCandidates.isNotEmpty())) {
            val before = runCatching { currentInputConnection?.getTextBeforeCursor(maxOf(25, current.pendingEnglishText.length), 0)?.toString() }.getOrNull()
            val contextChanged = before != null && if (current.pendingEnglishText.isNotEmpty())
                !before.endsWith(current.pendingEnglishText) else !predictionManager.matchesCommittedContext(before)
            if (newSelStart != newSelEnd || contextChanged) {
                predictionManager.invalidatePendingPredictions()
                before?.let(predictionManager::replaceCommittedText)
                candidateState.value = current.copy(associationCandidates = emptyList(), pendingEnglishText = "")
                keyRouter.resetHardwareCandidateSelection()
            }
        }
        if (hasHardwareKeyboard && hardwareCursorImmediateOnly) {
            try { currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) } catch (_: Exception) { }
        }
    }

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        if (!hasHardwareKeyboard) return
        try {
            val anchor = resolveHardwareCursorAnchor(info)
            uiState.value = uiState.value.copy(cursorAnchor = anchor, editorBounds = resolveHardwareEditorBounds(info))
            debugLog("Hardware cursor anchor=$anchor markerFlags=${info.insertionMarkerFlags}")
        } catch (e: Exception) {
            uiState.value = uiState.value.copy(cursorAnchor = null, editorBounds = null)
            FileLogger.e(TAG, "onUpdateCursorAnchorInfo failed", e)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        // 悬浮条状态下再点输入框：App 会再次请求弹出键盘，换回完整键盘；语音照常听写，与展开按钮相同。
        if (voicePillExpandsOnShowRequest(uiState.value.voiceCollapsed, pendingVoicePill, configChange)) expandFromVoicePill()
        return true
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        currentResolvedKeyboardSize = null
        clearHardwareSurfaceBounds()
        uiState.value = uiState.value.copy(cursorAnchor = null, editorBounds = null)
        updateHardwareKeyboardConnection()
        super.onConfigurationChanged(newConfig)
        if (newConfig.screenWidthDp > newConfig.screenHeightDp) {
            closeToolPanel()
        }
        applyCompactMode()
        loadDarkModePreference()
        applyWindowBackground()
        requestHardwareCursorUpdates()
    }

    private var appliedIncognito: Boolean? = null

    /** 引擎学习开关跟随生效中的无痕；在输入队列中执行，与按键保持先后顺序。 */
    private fun applyIncognito() {
        val active = com.kingzcheung.xime.settings.IncognitoState.active
        if (appliedIncognito == active) return
        appliedIncognito = active
        inputCommands.submit { rimeEngine.setIncognito(active) }
    }

    internal fun applyWindowBackground() {
        val state = uiState.value
        val isDark = isDarkTheme()
        try {
            val theme = com.kingzcheung.xime.ui.theme.KeyboardThemes.getRenderingScheme(state.themeId)
            // 图片背景无法映射到 window 层，用主题主色作为导航栏/窗口兜底色；
            // solid / gradient 用解析出的键盘背景兜底色。
            val bgColor = if (theme.keyboardBackground?.type == "image") {
                com.kingzcheung.xime.ui.theme.KeyboardThemes.getPrimaryColor(state.themeId, isDark)
            } else {
                com.kingzcheung.xime.ui.theme.KeyboardThemes.getKeyboardBackgroundColor(state.themeId, isDark)
            }
            val argb = (bgColor.alpha * 255).toInt() shl 24 or
                (bgColor.red * 255).toInt() shl 16 or
                (bgColor.green * 255).toInt() shl 8 or
                (bgColor.blue * 255).toInt()
            window.window?.let { win ->
                if (state.isCompact || state.voiceCollapsed) {
                    // 硬件键盘候选栏模式：窗口透明
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                    win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    win.setDimAmount(0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        win.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
                    }
                } else if (state.isFloatingMode) {
                    win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    win.setDimAmount(0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        win.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
                    }
                } else {
                    // 非浮动模式：参考成熟输入法 FULL 方案的背景/高度布局。
                    // 1) edge-to-edge：窗口绘制到系统导航栏后面，键盘背景（渐变/图片）可延伸到底部；
                    // 2) 窗口背景透明：键盘内容由 Compose 绘制，键盘上方露出应用内容而不是白色/主题色块；
                    // 3) 直角模式由 Compose 填充导航区；圆角模式保留透明并启用系统对比度保护。
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                    win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    win.setDimAmount(0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        win.isNavigationBarContrastEnforced = SettingsPreferences.roundedKeyboardBottom(this)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        win.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    win.decorView?.let { decor ->
                        val controller = androidx.core.view.WindowInsetsControllerCompat(win, decor)
                        val rounded = SettingsPreferences.roundedKeyboardBottom(this)
                        val systemDark = (resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                        val colors = KeysConfigHelper.getKeyboardColors()
                        val textValue = if (isDark) colors.keyTextColorDark else colors.keyTextColor
                        val textColor = com.kingzcheung.xime.ui.theme.KeyboardThemes.getKeyTextColorOverride(state.themeId, isDark)
                            ?: androidx.compose.ui.graphics.Color(if (textValue > 0xFFFFFF) textValue else (0xFF000000L or textValue))
                        controller.isAppearanceLightNavigationBars =
                            com.kingzcheung.xime.ui.keyboard.useDarkNavigationIcons(rounded, systemDark, textColor.luminance())
                    }
                }
                // setDecorFitsSystemWindows(false) 后必须重新分发 insets，
                // 否则 onApplyWindowInsets 不会触发、底部导航栏高度检测不到。
                win.decorView?.requestApplyInsets()
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "applyWindowBackground failed", e)
        }
    }

    private fun applyCompactMode() {
        val current = uiState.value
        val isCompact = hardwareKeyboardDisplay.compact
        FileLogger.i(
            TAG,
            "applyCompactMode: keyboardCfg=${keyboardConfigName(resources.configuration.keyboard)}, " +
                "hasHardwareKeyboard=$hasHardwareKeyboard, isCompact=$isCompact (was ${current.isCompact})"
        )
        if (current.isCompact != isCompact) {
            if (isCompact) {
                // A keyboard may be attached while a full-screen editor/resize layer is open.
                // Those layers must not keep a full-window touch region behind the toolbar.
                cancelKeyboardResize()
                keyboardCallbacks?.onDismissPreeditEditor?.invoke()
                if (uiState.value.isVoiceMode) endVoiceSession()
                closeToolPanel()
                keyboardViewModel.closeOverlay()
                keyboardViewModel.setCandidatePageExpanded(false)
                uiState.value = uiState.value.copy(
                    isCompact = true, voiceCollapsed = false, handwritingExpanded = false, clipboardImagesExpanded = false,
                    showQuickSendForm = false, quickSendFormFocused = false, quickSendCodeFocused = false,
                )
            } else uiState.value = uiState.value.copy(isCompact = false)
            refreshKeyboardGeometry()
            applyWindowBackground()
        }
    }

    private fun showScreenKeyboardFromHardware(route: OverlayRoute? = null) {
        if (!hasHardwareKeyboard) return
        hardwareKeyboardDisplay = hardwareKeyboardDisplay.showOnScreen()
        if (route == null) keyboardViewModel.closeOverlay() else keyboardViewModel.showOverlay(route)
        applyCompactMode()
    }

    /** Collapse only the surface: the current composing text and candidate session stay alive. */
    private fun collapseScreenKeyboardToHardwareToolbar(): Boolean {
        if (!hasHardwareKeyboard || !hardwareKeyboardDisplay.screenKeyboardRequested) return false
        hardwareKeyboardDisplay = hardwareKeyboardDisplay.collapseToToolbar()
        applyCompactMode()
        requestHardwareCursorUpdates()
        return true
    }

    private fun clearHardwareSurfaceBounds() {
        hardwareToolbarBounds.value = null
        hardwareCandidateBounds = null
        hardwarePreeditBounds = null
        updateGlassSurface("hardware-toolbar", null)
        updateGlassSurface("hardware-candidates", null)
        updateGlassSurface("hardware-preedit", null)
    }

    private fun updateHardwarePreeditBounds(bounds: android.graphics.Rect?) {
        val next = bounds?.takeIf { uiState.value.isCompact && !it.isEmpty }?.let { android.graphics.Rect(it) }
        if (hardwarePreeditBounds == next) return
        hardwarePreeditBounds = next
        updateGlassSurface("hardware-preedit", next)
        if (::keyboardContainer.isInitialized) keyboardContainer.requestLayout()
    }

    private fun updateHardwareSurfaceBounds(toolbar: Boolean, bounds: android.graphics.Rect?) {
        val current = if (toolbar) hardwareToolbarBounds.value else hardwareCandidateBounds
        val next = bounds?.takeIf { (uiState.value.isCompact || uiState.value.voiceCollapsed) && !it.isEmpty }?.let { android.graphics.Rect(it) }
        if (current == next) return
        if (toolbar) hardwareToolbarBounds.value = next else hardwareCandidateBounds = next
        updateGlassSurface(if (toolbar) "hardware-toolbar" else "hardware-candidates", next)
        if (::keyboardContainer.isInitialized) keyboardContainer.requestLayout()
    }

    private fun keyboardConfigName(value: Int): String = when (value) {
        android.content.res.Configuration.KEYBOARD_UNDEFINED -> "UNDEFINED"
        android.content.res.Configuration.KEYBOARD_NOKEYS -> "NOKEYS"
        android.content.res.Configuration.KEYBOARD_QWERTY -> "QWERTY"
        android.content.res.Configuration.KEYBOARD_12KEY -> "12KEY"
        else -> "UNKNOWN($value)"
    }

    private fun moveFloatingWindow(dx: Int, dy: Int) {
        window.window?.let { win ->
            val lp = win.attributes
            if (lp.gravity != (android.view.Gravity.TOP or android.view.Gravity.START)) {
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            lp.x = (lp.x + dx).coerceAtLeast(0)
            lp.y = (lp.y + dy).coerceAtLeast(0)
            win.attributes = lp
        }
    }

    private fun updateEnterKeyText(editorInfo: EditorInfo) {
        uiState.value = uiState.value.copy(enterKeyText = enterKeyTextFor(editorInfo))
    }

    /**
     * 关掉工具面板、快捷发送表单后回车键回到当前输入框自己的样子。原来写死“发送”，
     * 切悬浮会顺带关工具面板，同一个输入框的 ↵ 就变成了发送箭头。
     */
    internal fun editorEnterKeyText(): String = currentInputEditorInfo?.let(::enterKeyTextFor) ?: "发送"

    private fun enterKeyTextFor(editorInfo: EditorInfo): String {
        val imeOptions = editorInfo.imeOptions
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val enterText = when {
            noEnterAction -> "换行"
            action == EditorInfo.IME_ACTION_GO -> "前往"
            action == EditorInfo.IME_ACTION_SEARCH -> "搜索"
            action == EditorInfo.IME_ACTION_SEND -> "发送"
            action == EditorInfo.IME_ACTION_NEXT -> "下一项"
            action == EditorInfo.IME_ACTION_DONE -> "完成"
            else -> "换行"
        }
        return enterText
    }

    override fun onFinishInput() {
        debugLog("onFinishInput page=${keyboardViewModel.page.value::class.simpleName}")
        // 焦点离开输入框：不再自动弹回语音悬浮条。
        mainHandler.removeCallbacks(reviveVoicePill)
        voiceReviveCandidate = false
        pendingVoicePill = false
        englishEditorReadCapability.clear()
        inputStallWatchdog?.stop()
        hardwareLanguageShortcuts.reset()
        clipboardKeyboard.reset()
        clipboardNavigationKeys.clear()
        hardwareCandidateNavigationKeys.clear()
        glossShiftKeys.clear()
        com.kingzcheung.xime.gloss.CandidateGlossary.physicalShiftHeld.value = false
        com.kingzcheung.xime.gloss.CandidateGlossary.t9NextHeld.value = false
        clipboardRestoreCompact = false
        microphoneRestoreCompact = false
        hardwareCursorImmediateOnly = false
        clearHardwareSurfaceBounds()
        try { currentInputConnection?.requestCursorUpdates(0) } catch (_: Exception) { }
        uiState.value = uiState.value.copy(cursorAnchor = null, editorBounds = null)
        discardInlinePinyinBeforeFrameworkFinish()
        super.onFinishInput()
        if (::clipboardManager.isInitialized) clipboardManager.images.dismissPresentedPreview()
        runtimeObserver?.stop()
        inlineSuggestionManager?.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        clearInputState()
        recentClipboardItemsState.value = emptyList()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 内存回收会让词库与语法文件的缓存页失效，之后的按键变慢；记下级别供对照。
        FileLogger.w("InputMemory", "trim level=$level")
        // 系统缺内存而键盘没在显示：不必等 3 分钟，立刻卸掉联想模型（下次弹出键盘自动重载）。
        // 键盘显示中不动它，打字不能因为内存回收而卡。
        if (!isInputViewShown && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            mainHandler.removeCallbacks(releaseIdlePredictionModel)
            releaseIdlePredictionModel.run()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        val voiceState = uiState.value
        voiceReviveCandidate = !finishingInput && !voicePillDismissed && voiceState.isVoiceMode && voiceState.voiceSticky &&
            !voiceState.isCompact && !voiceState.voiceCollapsed
        discardInlinePinyinBeforeFrameworkFinish()
        // The search bar belongs to this keyboard session; leaving restores learning.
        closeClipboardSearch(openBoard = false)
        super.onFinishInputView(finishingInput)
        runtimeObserver?.stop()
        voiceRecognitionHandler.endInputSession()
        // 手写模型轻量，按"用键盘时加载、键盘收起即卸载"管理：输入会话结束
        // （收起键盘/焦点离开）即释放，:inference 侧同步卸载模型；未初始化时
        // release() 幂等空操作。下次落笔由 predict 自愈或布局重建重载。
        com.kingzcheung.xime.handwriting.HandwritingEngine.release()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        mainHandler.removeCallbacks(reviveVoicePill)
        if (voiceReviveCandidate) mainHandler.postDelayed(reviveVoicePill, 300)
        voiceReviveCandidate = false
        voicePillDismissed = false
        glassWindowVisible = false
        glassBlurWindows.values.forEach { it.setVisible(false) }
        com.kingzcheung.xime.ocr.DocumentOcrActivity.finishHiddenHost()
        clearHardwareSurfaceBounds()
        if (::clipboardManager.isInitialized) clipboardManager.images.dismissPresentedPreview()
        runtimeObserver?.stop()
        clipboardSyncBridge?.setKeyboardVisible(false)
        cancelKeyboardResize()
        clearInputState()
        recentClipboardItemsState.value = emptyList()
        mainHandler.removeCallbacks(releaseIdlePredictionModel)
        mainHandler.postDelayed(releaseIdlePredictionModel, PREDICTION_MODEL_IDLE_RELEASE_MS)
        // 键盘隐藏时重置快捷发送表单临时态：表单开启状态下直接隐藏键盘（未走 onClose）
        // 会把 showQuickSendForm 残留到下次弹窗，候选栏上方渲染出残留表单背景造成遮挡；
        // 回车键文案也需一并还原（打开表单时被改为"确定"）。
        if (uiState.value.showQuickSendForm || uiState.value.quickSendFormFocused) {
            uiState.value = uiState.value.copy(
                showQuickSendForm = false,
                ocrDraft = false,
                quickSendFormFocused = false,
                quickSendCodeFocused = false,
                quickSendEditingItemId = null,
                quickSendEditingItemText = "",
                quickSendEditingItemCode = "",
                enterKeyText = "发送",
            )
            QuickSendFormEditTextHolder.editText = null
            QuickSendFormCodeEditTextHolder.editText = null
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        mainHandler.removeCallbacks(reviveVoicePill)
        voicePillDismissed = false
        if (pendingVoicePill) {
            pendingVoicePill = false
            mainHandler.post {
                if (!uiState.value.isVoiceMode && !uiState.value.isCompact) {
                    uiState.value = uiState.value.copy(voiceCollapsed = true)
                    applyWindowBackground()
                    animateVoicePillIn()
                    keyboardCallbacks?.onVoiceStickyToggle?.invoke()
                }
            }
        }
        glassWindowVisible = true
        glassBlurWindows.values.forEach { it.setVisible(true) }
        openPendingOcrPanel()
        requestHardwareCursorUpdates()
        startRuntimeObservation()
        // 键盘弹出时对比系统取色与缓存的动态主题色，壁纸取色变化则重建主题并热更新 UI。
        // 每次弹出只做两次资源读取对比，取色未变时零成本。
        KeyboardThemes.refreshDynamicSchemes(this)
        clipboardSyncBridge?.setKeyboardVisible(true)
        mainHandler.removeCallbacks(releaseIdlePredictionModel)
        serviceScope.launch { AssociationManager.restoreModelAfterIdle() }
    }

    private fun openPendingOcrPanel() {
        if (com.kingzcheung.xime.ocr.DocumentOcrActivity.takePending()) {
            showScreenKeyboardFromHardware(OverlayRoute.Ocr)
            keyboardViewModel.showOverlay(OverlayRoute.Ocr)
        }
    }
    
    private fun synchronizeKeyboardInputSession() {
        val state = uiState.value
        keyboardViewModel.synchronizeInputSession(
            state.inputSessionId, state.t9ResetSignal, state.isAsciiMode, state.currentSchemaId,
        )
    }

    private fun clearInputState() {
        inputReadiness.newEditorSession()
        keyRouter.discardPendingCandidateCommit()
        if (uiState.value.handwritingExpanded) uiState.value = uiState.value.copy(handwritingExpanded = false)
        if (uiState.value.voiceCollapsed) uiState.value = uiState.value.copy(voiceCollapsed = false)
        voiceRecognitionHandler.endInputSession()
        // 即使 Compose 在隐藏时尚未销毁，旧手写回调也不能再写入宿主。
        uiState.value = uiState.value.copy(inputSessionId = System.nanoTime())
        closeToolPanel()
        // 输入会话结束：关闭残留的面板页面（表情/符号等 overlay），
        // 避免下次键盘弹出时在候选栏上方渲染上次的面板背景
        var page = keyboardViewModel.page.value
        while (page is com.kingzcheung.xime.keyboard.KeyboardPage.Overlay) {
            keyboardViewModel.closeOverlay()
            page = keyboardViewModel.page.value
        }
        calculatorEngine.clear()
        inputCommands.submit { rimeEngine.clearQueuedComposition(); rimeEngine.clearQueuedT9Composition() }
        t9PartialSegments.clear()
        // 录音已在本方法入口停止；这里仅重置语音 UI。
        if (uiState.value.isVoiceMode || voiceRecordingStarted) {
            isTrackingVoiceButtons = false
            voiceRecordingStarted = false
            voiceAmplitudeState.floatValue = 0f
            uiState.value = uiState.value.copy(
                isVoiceMode = false,
                voiceSticky = false,
                voiceButtonState = VoiceButtonState(),
                voiceRecognitionState = RecognitionState.IDLE,
                voiceRecognizedText = "",
                voiceAmplitude = 0f
            )
            keyboardViewModel.exitVoice()
        }
        uiState.value = uiState.value.copy(
            t9ResetSignal = uiState.value.t9ResetSignal + 1,
            t9RightCandidateSelectedCount = 0,
            t9SelectedCandidatePinyin = ""
        )
        synchronizeKeyboardInputSession()
        candidateState.value = candidateState.value.copy(
            candidates = emptyList(),
            candidateComments = emptyList(),
            inputText = "",
            isComposing = false,
            isShowingRecentClipboard = false,
            associationCandidates = emptyList(),
            pendingEnglishText = "",
            hasNextPage = false,
            hasPrevPage = false,
            englishReplaceSupported = true,
            candidateActions = emptyList()
        )
        endComposingInputBox()
    }

    /**
     * 输入框是否存在 IME 写入的 composing 区域（拼音编码回显 / 语音识别临时文本）。
     * Android 无法查询宿主编辑器的 composing 状态，由写入点主动标记。
     */
    private val inlineComposition = InlineCompositionTracker()
    /** 上一个输入框（包名、fieldId、inputType），判断重启输入是否仍是同一个框。 */
    private var lastEditorIdentity: Triple<String?, Int, Int>? = null

    /** 标记刚向输入框写入了 composing 文本；拼音编码同时记下文字（语音 partial 不记）。 */
    internal fun markInputBoxComposing(text: String? = null) {
        inlineComposition.shown(text)
    }

    /** 编辑时保留已显示文字，同时清理标志，避免稍后把选区当 composing 删除。 */
    internal fun finishComposingInputBoxKeepingText() {
        currentInputConnection?.finishComposingText()
        inlineComposition.ended()
    }

    /**
     * 框架结束或重建输入连接时会把组合区定格成普通文字。未完成的拼音编码不是用户要的文字：
     * 收起键盘、离开输入框前先撤掉，与编码显示在候选栏时一致（语音临时文本由语音收尾处理）。
     */
    private fun discardInlinePinyinBeforeFrameworkFinish() {
        if (inlineComposition.text != null) endComposingInputBox()
    }

    /**
     * 宿主在拼音组合中重启输入（setText、改 inputType 等）时，框架已把编码定格成普通文字。
     * 仍是同一个输入框、且光标处的文字正好拼回 CyIME 刚写的编码时把它删掉；对不上就不碰任何文字。
     */
    private fun removeDetachedComposition(text: String, attribute: EditorInfo?, restarting: Boolean, sameField: Boolean) {
        val ic = currentInputConnection
        val selStart = attribute?.initialSelStart ?: -1
        val selEnd = attribute?.initialSelEnd ?: -1
        val window = text.length + 1
        fun before(): CharSequence? = (if (Build.VERSION.SDK_INT >= 30) attribute?.getInitialTextBeforeCursor(window, 0) else null)
            ?: runCatching { ic?.getTextBeforeCursor(window, 0) }.getOrNull()
        fun after(): CharSequence? = (if (Build.VERSION.SDK_INT >= 30) attribute?.getInitialTextAfterCursor(window, 0) else null)
            ?: runCatching { ic?.getTextAfterCursor(window, 0) }.getOrNull()
        val region = if (sameField && ic != null) locateDetachedComposition(text, selStart, selEnd, before(), after()) else null
        val removed = region != null && runCatching {
            ic!!.beginBatchEdit()
            try { ic.setComposingRegion(region.start, region.end) && ic.setComposingText("", 1) } finally { ic.endBatchEdit() }
        }.getOrDefault(false)
        // 只记长度与位置，不记文字内容。
        FileLogger.w("InputLifecycle", "inline composition detached: restarting=$restarting sameField=$sameField " +
            "len=${text.length} sel=$selStart/$selEnd located=${region != null} removed=$removed")
    }

    /**
     * 清理输入框中的 composing 区域（未上屏的拼音编码 / 语音临时文本）。
     *
     * 无论输入位置设置（输入框/候选栏）都执行。
     * 注意：composing 区域不存在时 [InputConnection.setComposingText] 并非空操作——
     * 它会在光标处"插入"空串，光标处有选中文字时等于删除整个选区
     * （收起键盘/焦点切换误删选中文字的根因）。因此仅在标记过 composing 时才清空，
     * 否则只调用 finishComposingText（无 composing 时是无害空操作，仅兜底清理残留 span）。
     */
    internal fun endComposingInputBox() {
        currentInputConnection?.let {
            if (inlineComposition.active) {
                it.setComposingText("", 0)
                it.finishComposingText()
            } else {
                it.finishComposingText()
            }
        }
        inlineComposition.ended()
    }

    /**
     * 当前输入框是否受限（密码/终端/NO_SUGGESTIONS，见 EditorInfoClassifier）：
     * 英文联想等补全类功能应短路。
     */
    internal fun isEditorRestricted(): Boolean = editorRestricted

    internal fun isSecretEditor(): Boolean = editorSecret

    /**
     * 当前宿主是否支持英文候选的"回删替换"机制。
     *
     * 英文直接上屏模式下，选中候选词需要 deleteSurroundingText 回删已上屏编码再提交候选词；
     * 终端等受限宿主对该能力（含文本探测接口）通常不支持，探针返回 null。
     * 此类宿主直接不提供英文联想候选。
     */
    private val englishEditorReadCapability = EditorReadCapability()

    internal fun supportsEnglishCandidateReplace(): Boolean {
        if (isSecretEditor()) return false
        if (uiState.value.quickSendFormFocused || uiState.value.toolPanelInputFocused) return true
        val ic = currentInputConnection ?: return false
        return englishEditorReadCapability.supports(uiState.value.inputSessionId, ic) {
            runCatching { ic.getTextBeforeCursor(1, 0) != null }.getOrDefault(false)
        }
    }

    override fun onDestroy() {
        if (com.kingzcheung.xime.ui.keyboard.HandwritingGlassRegion.reporter === handwritingGlassRegionReporter)
            com.kingzcheung.xime.ui.keyboard.HandwritingGlassRegion.reporter = null
        glassBlurWindows.values.forEach { it.dispose() }
        glassBlurWindows.clear()
        glassSurfaceBounds.clear()
        hardwareInputManager.unregisterInputDeviceListener(hardwareInputListener)
        inputStallWatchdog?.stop()
        runtimeObserver?.stop()
        inputReadiness.close()
        serviceScope.cancel()
        super.onDestroy()
        sharedPrefsListener?.let {
            SettingsPreferences.getPrefsPublic(this).unregisterOnSharedPreferenceChangeListener(it)
        }
        RimeEngine.setDeploymentCallback { _, _ -> }
        stopClipboardSync()
        if (::clipboardManager.isInitialized) {
            clipboardManager.release()
        }
        _viewModelStore.clear()
        feedbackManager.release()
        rimeEngine.destroy()
        mainHandler.removeCallbacks(releaseIdlePredictionModel)
        AssociationManager.release()
        voiceRecognitionHandler.release()
        com.kingzcheung.xime.handwriting.HandwritingEngine.release()
        ExtensionManager.release()
        com.kingzcheung.xime.association.NativeOnnxEngine.releaseSharedEnv()
        keyProcessingDispatcher.close()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    internal fun hideKeyboard() {
        if (collapseScreenKeyboardToHardwareToolbar()) return
        // 语音听写中：收起成底部悬浮条，语音继续（仿 Gboard），不结束本次听写。
        if (collapseToVoicePill()) return
        clearInputState()
        requestHideSelf(0)
    }
    
    private fun canCollapseToVoicePill(): Boolean = uiState.value.let {
        it.isVoiceMode && it.voiceSticky && !it.voiceCollapsed && !it.isCompact
    }

    private fun collapseToVoicePill(): Boolean {
        if (!canCollapseToVoicePill()) return false
        keyboardViewModel.closeOverlay()
        closeToolPanel()
        // 键盘先淡出下沉，再换成悬浮条淡入上浮（用户 10-20：收起、弹出、消失都要动效）。
        swapKeyboardContent {
            uiState.value = uiState.value.copy(voiceCollapsed = true, handwritingExpanded = false)
            applyWindowBackground()
        }
        return true
    }

    private fun expandFromVoicePill() {
        if (!uiState.value.voiceCollapsed) return
        swapKeyboardContent {
            clearHardwareSurfaceBounds()
            uiState.value = uiState.value.copy(voiceCollapsed = false)
            applyWindowBackground()
        }
    }

    /** 语音悬浮条正在屏幕上（由悬浮条自己的组合进出维护），收起窗口前据此决定要不要先播退场。 */
    internal var voicePillOnScreen = false
    private var animatedHidePending = false

    /**
     * 整块输入法内容先淡出并下沉 [CONTENT_SHIFT_DP]，执行 [change]（换成键盘或悬浮条），再淡入上浮。
     * 时长沿用统一动效：退场 180ms、进场 200ms。
     */
    private fun swapKeyboardContent(change: () -> Unit) {
        if (!::keyboardContainer.isInitialized || !isInputViewShown) { change(); return }
        val shift = CONTENT_SHIFT_DP * resources.displayMetrics.density
        keyboardContainer.animate().cancel()
        keyboardContainer.animate().alpha(0f).translationY(shift)
            .setDuration(com.kingzcheung.xime.ui.theme.CyMotion.LONG_EXIT.toLong())
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                change()
                keyboardContainer.animate().alpha(1f).translationY(0f)
                    .setDuration(com.kingzcheung.xime.ui.theme.CyMotion.MEDIUM.toLong())
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }.start()
    }

    /** 悬浮条出现（失焦后自动重新弹出等）：从下方淡入上浮。 */
    internal fun animateVoicePillIn() {
        if (!::keyboardContainer.isInitialized) return
        keyboardContainer.animate().cancel()
        keyboardContainer.alpha = 0f
        keyboardContainer.translationY = CONTENT_SHIFT_DP * resources.displayMetrics.density
        keyboardContainer.animate().alpha(1f).translationY(0f)
            .setDuration(com.kingzcheung.xime.ui.theme.CyMotion.MEDIUM.toLong())
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    /** 悬浮条在屏幕上时，窗口收起（点别处失焦、系统收起）先播退场再真正收起，不再一下子消失。 */
    override fun hideWindow() {
        if (!voicePillOnScreen || animatedHidePending || !::keyboardContainer.isInitialized || !isInputViewShown) {
            super.hideWindow(); return
        }
        animatedHidePending = true
        keyboardContainer.animate().cancel()
        keyboardContainer.animate().alpha(0f).translationY(CONTENT_SHIFT_DP * resources.displayMetrics.density)
            .setDuration(com.kingzcheung.xime.ui.theme.CyMotion.LONG_EXIT.toLong())
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                animatedHidePending = false
                superHideWindow()
                keyboardContainer.alpha = 1f
                keyboardContainer.translationY = 0f
            }.start()
    }

    private fun superHideWindow() = super.hideWindow()

    override fun showWindow(showInput: Boolean) {
        // 退场动画中又要弹出：取消退场，内容恢复原样。
        if (animatedHidePending && ::keyboardContainer.isInitialized) {
            animatedHidePending = false
            keyboardContainer.animate().cancel()
            keyboardContainer.alpha = 1f
            keyboardContainer.translationY = 0f
        }
        super.showWindow(showInput)
    }

    // 语音听写中输入框被意外收起（系统收起键或 App 隐藏键盘、焦点仍在）：重新弹出悬浮条继续听写。
    private var voiceReviveCandidate = false
    private var voicePillDismissed = false
    private var pendingVoicePill = false
    private val reviveVoicePill = Runnable {
        if (!isInputViewShown && currentInputConnection != null) {
            pendingVoicePill = true
            requestShowSelf(0)
        }
    }

    private var compositionRefreshJob: kotlinx.coroutines.Job? = null

    /** Refresh requests never run JNI or plugin transforms on Main, nor fabricate an empty state on contention. */
    internal fun updateUI() {
        val admission = inputReadiness.ticket() ?: return
        serviceScope.launch(Dispatchers.Main.immediate) {
            if (!inputReadiness.accepts(admission)) return@launch
            compositionRefreshJob?.cancel()
            val session = uiState.value.inputSessionId
            compositionRefreshJob = launch(keyProcessingDispatcher) {
                val composition = rimeEngine.readQueuedComposition() ?: return@launch
                val transformed = if (composition.input.isNotEmpty()) candidateTransform.transform(
                    inputText = composition.input, preedit = composition.preedit,
                    engineCandidates = composition.candidates.toList(), asciiMode = composition.isAsciiMode,
                ) else null
                withContext(Dispatchers.Main) {
                    if (!inputReadiness.accepts(admission) || uiState.value.inputSessionId != session ||
                        !rimeEngine.isCandidateRevisionCurrent(composition.engineRevision)) return@withContext
                    if (transformed != null) sessionController.applyComposition(
                        composition.copy(candidates = transformed.candidates.toTypedArray()), transformed.actions,
                    ) else sessionController.applyComposition(composition)
                }
            }
        }
    }

    /**
     * 用户开始输入时清除候选栏中的 inline suggestions，让位于正常输入候选。
     */
    internal fun dismissInlineSuggestions() {
        inlineSuggestionManager?.clear()
    }


    

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (inlineSuggestionManager == null) return null
        updateInlineSuggestionTheme()
        val result = inlineSuggestionManager.onCreateInlineSuggestionsRequest(uiExtras)
        return result
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return inlineSuggestionManager?.onInlineSuggestionsResponse(response) ?: false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun updateInlineSuggestionTheme() {
        val state = uiState.value
        val isDark = when (state.darkMode) {
            1 -> true
            2 -> (resources.configuration.uiMode.and(
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            )) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
        val t = com.kingzcheung.xime.ui.theme.KeyboardThemes
        inlineSuggestionManager?.apply {
            val c = t.getCandidateTextColor(state.themeId, isDark)
            candidateTextColorArgb = (c.alpha * 255).toInt() shl 24 or
                (c.red * 255).toInt() shl 16 or
                (c.green * 255).toInt() shl 8 or
                (c.blue * 255).toInt()
            val label = c.copy(alpha = 0.6f)
            labelTextColorArgb = (label.alpha * 255).toInt() shl 24 or
                (label.red * 255).toInt() shl 16 or
                (label.green * 255).toInt() shl 8 or
                (label.blue * 255).toInt()
            isDarkTheme = isDark
        }
    }

    private var handwritingNormalInsetDp = 0
    private var handwritingOverlayActive = false

    override fun onComputeInsets(outInsets: Insets) {
        val state = uiState.value
        if ((state.handwritingExpanded || handwritingOverlayActive) &&
            (keyboardViewModel.page.value.underlyingPage() as? KeyboardPage.Main)?.type == com.kingzcheung.xime.keyboard.MainType.HANDWRITING) {
            val windowHeight = window.window?.decorView?.height ?: resources.displayMetrics.heightPixels
            val reserve = if (state.isFloatingMode) 0 else (handwritingNormalInsetDp * resources.displayMetrics.density).roundToInt()
            outInsets.contentTopInsets = (windowHeight - reserve).coerceAtLeast(0)
            outInsets.visibleTopInsets = outInsets.contentTopInsets
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
            return
        }
        if (state.showKeyboardResize) {
            // 调节层是全屏交互画布，但不能把宿主 App 顶到只剩一条缝。
            // 整个 IME 窗口可触摸；内容 inset 仍报告为“悬浮”，底层页面保持原位。
            outInsets.contentTopInsets = resources.displayMetrics.heightPixels
            outInsets.visibleTopInsets = resources.displayMetrics.heightPixels
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
            return
        }
        if (state.isCompact || state.voiceCollapsed) {
            val height = window.window?.decorView?.height ?: resources.displayMetrics.heightPixels
            // A toolbar resting on the bottom edge (Samsung bar or the Gboard pill) is reported as
            // occupied space, so apps lift their input field above it rather than under it.
            val bottomReserve = hardwareToolbarBounds.value?.takeIf {
                state.voiceCollapsed || height - it.bottom <= (24 * resources.displayMetrics.density).roundToInt()
            }?.top ?: height
            outInsets.contentTopInsets = bottomReserve.coerceIn(0, height)
            outInsets.visibleTopInsets = bottomReserve.coerceIn(0, height)
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.setEmpty()
            // Union visible surfaces, never their bounding rectangle: the transparent
            // area between the toolbar and caret must keep passing touches to the app.
            hardwareToolbarBounds.value?.let { outInsets.touchableRegion.op(it, android.graphics.Region.Op.UNION) }
            hardwarePreeditBounds?.let { outInsets.touchableRegion.op(it, android.graphics.Region.Op.UNION) }
            hardwareCandidateBounds?.let { outInsets.touchableRegion.op(it, android.graphics.Region.Op.UNION) }
            return
        } else if (state.isFloatingMode) {
            outInsets.apply {
                contentTopInsets = resources.displayMetrics.heightPixels
                visibleTopInsets = resources.displayMetrics.heightPixels
                touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                val decor = window.window?.decorView ?: return
                if (currentEffectiveKeyboardHeight <= 0) {
                    val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
                    val kbH = KeyboardHeightProfiles.floating(
                        this@XimeInputMethodService, isLandscape,
                        ((resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt() -
                            tryGetStatusBarHeightDp(this@XimeInputMethodService, window.window)).coerceAtLeast(1),
                    )
                    currentEffectiveKeyboardHeight = kbH + FLOATING_DRAG_BAR_HEIGHT_DP
                }
                val density = resources.displayMetrics.density
                val hostLocation = IntArray(2)
                if (::keyboardContainer.isInitialized) keyboardContainer.getLocationInWindow(hostLocation)
                val hostWidth = if (::keyboardContainer.isInitialized && keyboardContainer.width > 0)
                    keyboardContainer.width else decor.width
                val hostHeight = if (::keyboardContainer.isInitialized && keyboardContainer.height > 0)
                    keyboardContainer.height else decor.height
                // 卡片已测量到就直接用它；只有还没测到时才去算一次兜底尺寸（resolveKeyboardSize 不便宜）。
                val measuredCard = floatingCardBounds
                if (measuredCard != null) {
                    touchableRegion.set(measuredCard)
                } else {
                    val minimumBottom = maxOf(
                        tryGetVisibleNavBarHeightDp(this@XimeInputMethodService, window.window) * density,
                        bottomInsetPxState.value.toFloat(),
                    )
                    val fallbackSize = resolveKeyboardSize((hostWidth / density).roundToInt(),
                        (hostHeight / density).roundToInt(), kotlin.math.ceil(minimumBottom / density).toInt(), state)
                    val fallback = com.kingzcheung.xime.ui.keyboard.floatingCardRect(
                        hostWidth.toFloat(), hostHeight.toFloat(), fallbackSize.width * density,
                        (if (currentFloatingCardHeightDp > 0) currentEffectiveKeyboardHeight
                            else fallbackSize.height + FLOATING_DRAG_BAR_HEIGHT_DP) * density,
                        state.floatingOffsetX * density, state.floatingOffsetY * density, minimumBottom,
                    )
                    touchableRegion.set(
                        hostLocation[0] + fallback.left.roundToInt(),
                        hostLocation[1] + fallback.top.roundToInt(),
                        hostLocation[0] + fallback.right.roundToInt(),
                        hostLocation[1] + fallback.bottom.roundToInt(),
                    )
                }
                floatingCardBounds?.let { card -> splitKeyboardGap.hole(card.bottom.toFloat()) }?.let {
                    touchableRegion.op(it.left.roundToInt(), it.top.roundToInt(),
                        it.right.roundToInt(), it.bottom.roundToInt(), android.graphics.Region.Op.DIFFERENCE)
                }
            }
        } else {
            // 非浮动模式：窗口全屏，容器物理高度 = Compose 内容总高（含底部留白），
            // 容器在窗口内底部对齐（gravity BOTTOM）。
            // contentTopInsets 直接用容器顶部在窗口中的 y 同步计算：
            // 容器高度变化（面板撑高/收起）→ View relayout → traversal → 本方法
            // 自动以新几何重算并上报，无需 hack；窗口全屏时 super 会误判键盘占满
            // 全屏导致布局下沉，故必须显式报告容器顶部。
            if (::keyboardContainer.isInitialized && keyboardContainer.height > 0) {
                val loc = IntArray(2)
                keyboardContainer.getLocationInWindow(loc)
                val targetHeight = keyboardContainer.layoutParams.height
                val topPx = if (targetHeight > 0 && targetHeight != keyboardContainer.height) {
                    ((window.window?.decorView?.height ?: resources.displayMetrics.heightPixels) - targetHeight).coerceAtLeast(0)
                } else loc[1].coerceAtLeast(0)
                outInsets.contentTopInsets = topPx
                outInsets.visibleTopInsets = topPx
                // Transparent side gutters must not swallow touches to the host app.
                val card = fixedCardBounds
                val splitHole = card?.let { splitKeyboardGap.hole(it.bottom.toFloat()) }
                if (card != null && (card.width() < keyboardContainer.width || splitHole != null)) {
                    // 透明和触摸范围不改变固定键盘的避让高度。宿主底部操作栏也必须
                    // 避让整张卡片（包含工具栏/候选栏），不能仅依赖编辑框的自动平移。
                    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                    outInsets.touchableRegion.set(card.left, topPx, card.right, card.bottom)
                    // 分体中缝不拦截触摸，点到的是下面的应用。
                    splitHole?.let {
                        outInsets.touchableRegion.op(it.left.roundToInt(), it.top.roundToInt(),
                            it.right.roundToInt(), it.bottom.roundToInt(), android.graphics.Region.Op.DIFFERENCE)
                    }
                } else {
                    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
                }
            } else {
                super.onComputeInsets(outInsets)
            }
        }
    }

    /** 插件下行事件投递器（input_changed / text_committed，敏感输入豁免）。 */
    internal val pluginEvents = PluginEventDispatcher(this)

    /** 候选词变换协调器（插件 candidate_transform 能力，hotPath：key-processing 线程同步调用）。 */
    internal val candidateTransform = CandidateTransformCoordinator(this)

    override fun commitText(text: String) {
        commitTextAndPredict(text, isPaste = false)
    }

    /**
     * 粘贴性质上屏：保留文本提交事件并带 is_paste 标记，但不触发打字联想。
     * text_committed 事件语义是"文本上屏"
     * （照常投递给所有订阅插件），是否把粘贴计入打字量由插件自行决定
     * （typing-stats 过滤，用户反馈"一天一万多字"的主要来源即长文本粘贴）。
     */
    internal fun commitPastedText(text: String) {
        commitTextAndPredict(text, isPaste = true)
    }

    internal fun dismissPredictionsForPaste() {
        predictionManager.invalidatePendingPredictions()
        candidateState.value = candidateState.value.copy(
            associationCandidates = emptyList(), pendingEnglishText = "",
        )
    }

    private var verificationCodeJob: Job? = null

    internal fun cancelVerificationCodeInput() { verificationCodeJob?.cancel() }

    internal fun fillVerificationCode(code: String, sequential: Boolean) {
        if (!com.kingzcheung.xime.clipboard.VerificationCodeExtractor.isCode(code) ||
            candidateState.value.isComposing || keyRouter.hasPendingCandidateCommit ||
            uiState.value.quickSendFormFocused || uiState.value.toolPanelInputFocused) return
        cancelVerificationCodeInput()
        predictionManager.invalidatePendingPredictions()
        verificationCodeJob = serviceScope.launch {
            val commit: (String) -> Boolean = {
                commitTextSilently(it, isPaste = true, isSensitive = true) == TextCommitResult.ACCEPTED_HOST
            }
            // Numeric editors use digit commits even on the main code button, so
            // single-field and auto-advancing split-field forms share a safe path.
            val digitMode = sequential || currentInputEditorInfo?.inputType?.and(android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_NUMBER
            val accepted = if (digitMode) VerificationCodeInput.fill(code, target = {
                val info = currentInputEditorInfo
                if (info == null || currentInputConnection == null || !isInputViewShown ||
                    uiState.value.quickSendFormFocused || uiState.value.toolPanelInputFocused ||
                    candidateState.value.isComposing || keyRouter.hasPendingCandidateCommit) null
                else VerificationCodeInput.Target(info.packageName, info.fieldId, info.inputType, uiState.value.inputSessionId)
            }, commit = commit, content = {
                runCatching { currentInputConnection?.let { connection ->
                    val before = connection.getTextBeforeCursor(16, 0)
                    val after = connection.getTextAfterCursor(16, 0)
                    if (before == null || after == null) null else "$before|$after"
                } }.getOrNull()
            }) else commit(code)
            if (accepted) {
                recentClipboardItemsState.value.filter { it.text == code ||
                    com.kingzcheung.xime.clipboard.VerificationCodeExtractor.extract(it.text) == code
                }.forEach { clipboardManager.markConsumed(it.text) }
                candidateState.value = CandidateState()
            } else android.widget.Toast.makeText(this@XimeInputMethodService,
                "填入已停止，请检查输入框；未自动跳格时请手动逐格输入", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    internal fun submitText(text: String): TextCommitResult {
        InputCommandOwner.requireOwner().requireCurrent(inputReadiness, uiState.value.inputSessionId)
        return commitTextAndPredict(text, false)
    }

    internal fun commitTextAndPredict(text: String, isPaste: Boolean, allowPrediction: Boolean = true): TextCommitResult {
        // Invalidate before delivery: a queued prediction or English completion must
        // not reappear after a paste, including rejected/internal-editor pastes.
        if (isPaste) dismissPredictionsForPaste()
        val result = commitTextSilently(text, isPaste)
        if (result != TextCommitResult.ACCEPTED_HOST || isPaste || !allowPrediction) return result
        if (isSecretEditor() || editorInputPolicy.directInput) return result
        if (!canPredictAfter(text)) {
            predictionManager.invalidatePendingPredictions()
            candidateState.value = candidateState.value.copy(associationCandidates = emptyList(), pendingEnglishText = "")
            return result
        }
        if (isChineseMode) {
            val predictionAdmission = inputReadiness.ticket()
            val predictionEditor = uiState.value.inputSessionId
            val predictionRevision = predictionManager.requestRevision
            mainHandler.post {
                if (predictionAdmission != null && inputReadiness.accepts(predictionAdmission) &&
                    uiState.value.inputSessionId == predictionEditor && !uiState.value.isAsciiMode &&
                    predictionManager.requestRevision == predictionRevision) {
                    getPredictionFromPlugin(predictionManager.lastCommittedText)
                }
            }
        }
        return result
    }

    /**
     * 快捷发送表单内退格：按焦点路由到文本框/触发编码框，删除光标前字符或选区。
     * 与原行为对齐：表单显示即处理（未聚焦时删文本框），焦点在编码框时删编码框。
     * 需在主线程调用；返回 false 表示表单未显示（调用方继续常规退格流程）。
     */
    internal fun deleteInQuickSendForm(): Boolean {
        if (!uiState.value.showQuickSendForm) return false
        val et = if (uiState.value.quickSendFormFocused && uiState.value.quickSendCodeFocused)
            QuickSendFormCodeEditTextHolder.editText
        else QuickSendFormEditTextHolder.editText
        et?.let { box ->
            val start = box.selectionStart.coerceAtLeast(0)
            val end = box.selectionEnd.coerceAtLeast(start)
            if (end > start) {
                box.text?.delete(start, end)
                try { box.setSelection(start) } catch (_: Exception) {}
            } else if (start > 0) {
                box.text?.delete(start - 1, start)
                try { box.setSelection(start - 1) } catch (_: Exception) {}
            }
        }
        return true
    }

    /**
     * 静默上屏：与 [commitText] 相同的落盘路径（内部编辑器重定向、InputConnection、
     * text_committed 事件、联想上下文/输入统计），但不触发联想推理。
     * 手写叠写自动上屏/替换使用——笔画替换频率高，逐次推理既浪费又会闪烁候选栏。
     * [isPaste] 标记粘贴性质上屏，透传到 text_committed payload（见 commitPastedText）。
     * 需在主线程调用。
     */
    internal fun commitTextSilently(text: String, isPaste: Boolean = false, isSensitive: Boolean = false): TextCommitResult {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Editor commits belong to Main" }
        if (!isSensitive) cancelVerificationCodeInput()
        if (InputCommandOwner.current.get() == null) {
            check(com.kingzcheung.xime.rime.RimeCommandContext.validity.get() == null) {
                "Queued editor delivery lost its command owner"
            }
            // Explicit boundary for synchronous Main callbacks (clipboard/handwriting/voice).
            // Queued keyboard commands use submitText(), which requires an existing owner.
            val admission = inputReadiness.ticket() ?: return TextCommitResult.NO_CONNECTION
            val editor = uiState.value.inputSessionId
            val owner = InputCommandOwner(admission, editor) {
                if (!inputReadiness.accepts(admission) || uiState.value.inputSessionId != editor)
                    throw kotlinx.coroutines.CancellationException("Expired direct editor action")
            }
            return owner.runInline { commitTextSilently(text, isPaste, isSensitive) }
        }
        InputCommandOwner.requireOwner().requireCurrent(inputReadiness, uiState.value.inputSessionId)
        val state = uiState.value
        if (state.quickSendFormFocused || state.toolPanelInputFocused) {
            val editor = when {
                state.quickSendFormFocused && state.quickSendCodeFocused -> QuickSendFormCodeEditTextHolder.editText
                state.quickSendFormFocused -> QuickSendFormEditTextHolder.editText
                else -> ToolPanelEditTextHolder.editText
            } ?: return TextCommitResult.NO_CONNECTION
            val editable = editor.text ?: return TextCommitResult.NO_CONNECTION
            val start = editor.selectionStart.coerceIn(0, editable.length)
            val end = editor.selectionEnd.coerceIn(start, editable.length)
            editable.replace(start, end, text)
            editor.setSelection(start + text.length)
            return TextCommitResult.ACCEPTED_INTERNAL
        }
        val connection = currentInputConnection ?: return TextCommitResult.NO_CONNECTION
        if (!connection.commitText(text, 1)) return TextCommitResult.REJECTED
        if (isSensitive || isSecretEditor()) return TextCommitResult.ACCEPTED_HOST
        pluginEvents.onTextCommitted(text, isPaste)
        if (isChineseMode) {
            val before = runCatching { connection.getTextBeforeCursor(25, 0)?.toString() }.getOrNull()
            if (before != null) predictionManager.replaceCommittedText(before)
            else predictionManager.appendCommittedText(text)
            if (!isPaste) predictionManager.recordInput(text)
        }
        return TextCommitResult.ACCEPTED_HOST
    }

    /** 当前编辑目标的绝对光标位置，供手写候选验证文字所属位置。 */
    internal fun currentEditorCursorPosition(): Int? {
        val state = uiState.value
        val editor = when {
            state.quickSendFormFocused && state.quickSendCodeFocused -> QuickSendFormCodeEditTextHolder.editText
            state.quickSendFormFocused -> QuickSendFormEditTextHolder.editText
            state.toolPanelInputFocused -> ToolPanelEditTextHolder.editText
            else -> null
        }
        if (editor != null) return editor.selectionEnd.takeIf { it >= 0 }
        val extracted = currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        return extracted?.takeIf { it.startOffset >= 0 && it.selectionEnd >= 0 }
            ?.let { it.startOffset + it.selectionEnd }
    }

    /**
     * 光标前文本与 expected 相同时替换为 replacement，返回是否替换成功。
     * 焦点在输入法内部编辑器时作用于对应 EditText，否则宿主 InputConnection。
     * 不匹配时不做任何操作（调用方决定降级策略）。需在主线程调用。
     */
    internal fun replaceBeforeCursor(expected: String, replacement: String): Boolean {
        val quickSendFocused = uiState.value.quickSendFormFocused
        if (quickSendFocused || uiState.value.toolPanelInputFocused) {
            val et = when {
                quickSendFocused && uiState.value.quickSendCodeFocused ->
                    QuickSendFormCodeEditTextHolder.editText
                quickSendFocused -> QuickSendFormEditTextHolder.editText
                else -> ToolPanelEditTextHolder.editText
            } ?: return false
            if (et.selectionStart != et.selectionEnd) return false
            val selStart = et.selectionStart.coerceAtLeast(0)
            val start = (selStart - expected.length).coerceAtLeast(0)
            val before = et.text?.substring(start, selStart)
            if (before != expected) return false
            et.text?.replace(start, selStart, replacement)
            try { et.setSelection(start + replacement.length) } catch (_: Exception) {}
            return true
        }
        val ic = currentInputConnection ?: return false
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val before = runCatching {
            ic.getTextBeforeCursor(expected.length, 0)?.toString()
        }.getOrNull()
        if (before != expected) return false
        var replaced = false
        ic.beginBatchEdit()
        try {
            if (!ic.deleteSurroundingText(expected.length, 0)) return false
            replaced = ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
        return replaced
    }

    
    
}
