package com.kingzcheung.xime.ui.theme

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.BackgroundMotionEffect
import com.kingzcheung.xime.settings.BackgroundMotionPreferences
import com.kingzcheung.xime.settings.BackgroundMotionSettings
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 动态背景这一帧的样子：先以背景框中心放大 [scale] 倍（≥ 1），再平移 [shiftX]、[shiftY]（框宽、框高的比例）。
 * |shift| ≤ (scale − 1) / 2 时图片始终盖满原来的框，四边不会露出底色。
 */
internal data class BackgroundMotionTransform(val scale: Float, val shiftX: Float, val shiftY: Float) {
    val isIdentity: Boolean get() = scale == 1f && shiftX == 0f && shiftY == 0f

    companion object {
        val Identity = BackgroundMotionTransform(1f, 0f, 0f)
    }
}

/**
 * 动态背景的轨迹，只和走过的时间有关（键盘收起时时间停住，再弹出接着走，不跳）。
 *
 * - 放大：一段 [shotMs] 从原样慢慢放大到 [MAX_SCALE]，再用半段时间缓回原样。
 * - 缩小：反过来，从放大慢慢退回原样，再缓回放大。
 * - 平移：保持放大，从左慢慢移到右，再从右移回左。
 * - 轮换：放大并移向左边 → 平移到右边 → 缩回原样，三段首尾相接，不回跳。
 * 每段两头都缓入缓出。
 */
internal object BackgroundMotionPath {
    /** 放大 12%：图片四边各多出 6% 用来平移，键盘上看得出在动，又不至于把图裁掉太多。 */
    const val MAX_SCALE = 1.12f
    const val MAX_SHIFT = (MAX_SCALE - 1f) / 2f

    /** 最多每秒 20 帧：放大 12% 走 18 秒，每帧不到半个像素，再多帧也看不出更顺，只多耗电。 */
    const val FRAME_INTERVAL_MS = 50L

    /** 一圈多长：走完回到开头的样子。轮播在每圈结束时换下一张。 */
    fun cycleMs(effect: BackgroundMotionEffect, shotMs: Long): Long {
        val shot = shotMs.coerceAtLeast(1L)
        return when (effect) {
            BackgroundMotionEffect.ZOOM_IN, BackgroundMotionEffect.ZOOM_OUT -> shot + (shot / 2).coerceAtLeast(1L)
            BackgroundMotionEffect.PAN -> 2 * shot
            BackgroundMotionEffect.CYCLE -> 3 * shot
        }
    }

    /** 轮播到第几张（[count] 张，少于 2 张时总是 0）。 */
    fun slideIndex(effect: BackgroundMotionEffect, elapsedMs: Long, shotMs: Long, count: Int): Int =
        if (count < 2) 0 else ((elapsedMs.coerceAtLeast(0L) / cycleMs(effect, shotMs)) % count).toInt()

    fun at(effect: BackgroundMotionEffect, elapsedMs: Long, shotMs: Long): BackgroundMotionTransform {
        val shot = shotMs.coerceAtLeast(1L)
        val elapsed = elapsedMs.coerceAtLeast(0L)
        return when (effect) {
            BackgroundMotionEffect.ZOOM_IN -> zoom(elapsed, shot, from = 1f, to = MAX_SCALE)
            BackgroundMotionEffect.ZOOM_OUT -> zoom(elapsed, shot, from = MAX_SCALE, to = 1f)
            BackgroundMotionEffect.PAN -> {
                val t = elapsed % (2 * shot)
                val x = if (t < shot) lerp(-MAX_SHIFT, MAX_SHIFT, ease(t, shot))
                    else lerp(MAX_SHIFT, -MAX_SHIFT, ease(t - shot, shot))
                BackgroundMotionTransform(MAX_SCALE, x, 0f)
            }
            BackgroundMotionEffect.CYCLE -> {
                val t = elapsed % (3 * shot)
                when {
                    t < shot -> ease(t, shot).let { p -> BackgroundMotionTransform(lerp(1f, MAX_SCALE, p), -MAX_SHIFT * p, 0f) }
                    t < 2 * shot -> BackgroundMotionTransform(MAX_SCALE, lerp(-MAX_SHIFT, MAX_SHIFT, ease(t - shot, shot)), 0f)
                    else -> ease(t - 2 * shot, shot).let { p -> BackgroundMotionTransform(lerp(MAX_SCALE, 1f, p), MAX_SHIFT * (1f - p), 0f) }
                }
            }
        }
    }

    private fun zoom(elapsed: Long, shot: Long, from: Float, to: Float): BackgroundMotionTransform {
        // 缓回用半段时间：再快就看得出是在倒回去，每帧也会超过一个像素。
        val back = (shot / 2).coerceAtLeast(1L)
        val t = elapsed % (shot + back)
        val scale = if (t < shot) lerp(from, to, ease(t, shot)) else lerp(to, from, ease(t - shot, back))
        return BackgroundMotionTransform(scale, 0f, 0f)
    }

    private fun ease(t: Long, duration: Long): Float {
        val p = (t.toFloat() / duration).coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
}

/**
 * 动态背景什么时候动。开关关着、不是图片背景、当前应用是游戏、系统省电、系统关了动画：不动，按原样静止显示。
 * 键盘收起、面板或展开候选盖住键盘时只是暂停（[KeyboardBackgroundMotion.covered]），位置留着。
 */
internal object BackgroundMotionGate {
    fun systemAllows(powerSave: Boolean, animationsOn: Boolean): Boolean = !powerSave && animationsOn

    /** [surface]：键盘本体在显示（不是实体键盘浮条、语音悬浮条）。 */
    fun allowed(enabled: Boolean, imageBackground: Boolean, game: Boolean, systemAllows: Boolean, surface: Boolean): Boolean =
        enabled && imageBackground && !game && systemAllows && surface
}

/**
 * 键盘里共用的一只钟：所有画背景图的地方读同一个 [transform]，导航条、两侧留空和键盘本体一起动。
 * [slides] 是「我的图片」轮播的全部图片（主图在前），少于 2 张不轮播。
 */
@Stable
internal class KeyboardBackgroundMotion(
    val effect: BackgroundMotionEffect,
    private val shotMs: Long,
    val slides: List<String> = emptyList(),
) {
    private val elapsed = mutableLongStateOf(0L)

    private val slide = mutableIntStateOf(0)

    /** 现在轮到的那张图（themes/…）；不轮播时 null。每圈才变一次，在组合里读也只在换图时重组。 */
    val slideSource: String? get() = if (slides.size < 2) null else slides[slide.intValue.coerceIn(0, slides.size - 1)]

    /** 面板、展开候选盖住了键盘：钟停下，不再出新帧。由 KeyboardView 设。 */
    var covered by mutableStateOf(false)

    /** 只在画的阶段读（graphicsLayer 块或 draw 块），不引起重组。 */
    val transform: BackgroundMotionTransform get() = BackgroundMotionPath.at(effect, elapsed.longValue, shotMs)

    internal fun advance(ms: Long) {
        elapsed.longValue += ms.coerceIn(0L, MAX_STEP_MS)
        // 只在换图时写，读 slideSource 的地方每圈重组一次。
        val index = BackgroundMotionPath.slideIndex(effect, elapsed.longValue, shotMs, slides.size)
        if (index != slide.intValue) slide.intValue = index
    }

    private companion object {
        /** 主线程卡了一下之后不一步跳过去。 */
        const val MAX_STEP_MS = 100L
    }
}

internal val LocalKeyboardBackgroundMotion = staticCompositionLocalOf<KeyboardBackgroundMotion?> { null }

/**
 * 键盘本体背景图交给单独一层画（[BackgroundImageSlot]），每帧只改这一层的缩放和位移，按键和候选不重画。
 * [draw] 在本层坐标里按原样（不带动作）画图；[frame] 是图所在的框（缩放以它中心为准），null 为整层。
 */
@Stable
class BackgroundImageSlot {
    var draw: (DrawScope.() -> Unit)? by mutableStateOf(null)
        private set
    var frame: (() -> Rect?) by mutableStateOf({ null })
        private set
    /** 换图（轮播、换主题、重新渲染）时旧图留着淡出，[fade] 从 0 升到 1 时新图盖上来；见 KeyboardBackgroundMotionLayer。 */
    var previousDraw: (DrawScope.() -> Unit)? by mutableStateOf(null)
    val fade = mutableFloatStateOf(1f)
    var fadeToken by mutableIntStateOf(0)
        private set
    private var key: Any? = null

    /** [key] 是这张图本身（位图对象）：同一张图只换画法时不淡入淡出。 */
    fun update(key: Any?, frame: () -> Rect?, draw: (DrawScope.() -> Unit)?) {
        if (key != null && this.key != null && key !== this.key && this.draw != null) {
            previousDraw = this.draw
            fade.floatValue = 0f
            fadeToken++
        }
        this.key = key
        this.frame = frame
        this.draw = draw
    }
}

/**
 * 轮播：「我的图片」换成现在轮到的那张（第一张是主图，按调整页的摆放；其余铺满居中）。
 * 别的背景、没开轮播时原样返回。只在键盘里起作用（设置页预览没有钟）。
 */
@Composable
internal fun BackgroundConfig?.withCurrentSlide(): BackgroundConfig? =
    withCurrentSlide(LocalKeyboardBackgroundMotion.current)

@Composable
internal fun BackgroundConfig?.withCurrentSlide(motion: KeyboardBackgroundMotion?): BackgroundConfig? {
    val slide = motion?.slideSource ?: return this
    return remember(this, slide) { withSlide(slide) }
}

internal fun BackgroundConfig?.withSlide(slide: String?): BackgroundConfig? =
    if (slide == null || this == null || !CustomImageTheme.isCustomImage(this) || slide == src) this
    else copy(src = slide, srcDark = null, fit = "cover", zoom = null, alignX = null, alignY = null)

/** 按 [transform] 画 [frame] 里的背景图：不在独立图层里的背景（导航条、两侧留空、玻璃共用背景）用这个。 */
internal inline fun DrawScope.withBackgroundMotion(transform: BackgroundMotionTransform?, frame: Rect, block: DrawScope.() -> Unit) {
    if (transform == null || transform.isIdentity) { block(); return }
    withTransform({
        translate(transform.shiftX * frame.width, transform.shiftY * frame.height)
        scale(transform.scale, transform.scale, pivot = frame.center)
    }, block)
}

@Composable
internal fun rememberBackgroundMotionSettings(): BackgroundMotionSettings {
    val context = LocalContext.current
    var settings by remember(context) { mutableStateOf(BackgroundMotionPreferences.read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in BackgroundMotionPreferences.KEYS) settings = BackgroundMotionPreferences.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

/** 系统省电、系统关了动画（开发者选项「动画程序时长」为 0、无障碍「移除动画」）时不动。只在开关开着时才监听。 */
@Composable
internal fun rememberSystemAllowsBackgroundMotion(listen: Boolean): Boolean {
    if (!listen) return false
    val context = LocalContext.current.applicationContext
    var allowed by remember(context) { mutableStateOf(systemAllowsMotion(context)) }
    DisposableEffect(context) {
        val update = { allowed = systemAllowsMotion(context) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update() }
        }
        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { update() }
        }
        val observerRegistered = runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }.isSuccess
        update()
        onDispose {
            if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
            if (observerRegistered) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return allowed
}

private fun systemAllowsMotion(context: Context): Boolean {
    val powerSave = runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
    val animations = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
    return BackgroundMotionGate.systemAllows(powerSave, animations)
}

/**
 * 动态背景的钟。[allowed] 为假（开关关、不是图片背景、游戏、省电、关了动画）时返回 null，背景按原样画；
 * [running] 为假（键盘没显示）时钟停住。走钟只写一个数，读它的是各层的画图块：键盘本体只改图层属性，不重组、不重画按键。
 */
@Composable
internal fun rememberKeyboardBackgroundMotion(
    settings: BackgroundMotionSettings,
    allowed: Boolean,
    running: Boolean,
    /** 轮播的全部图片（主图在前）；不轮播时空。 */
    slides: List<String> = emptyList(),
): KeyboardBackgroundMotion? {
    if (!allowed) return null
    val motion = remember(settings.effect, settings.speed, slides) {
        KeyboardBackgroundMotion(settings.effect, settings.speed.shotMs, slides)
    }
    LaunchedEffect(motion, running) {
        if (!running) return@LaunchedEffect
        while (true) {
            snapshotFlow { motion.covered }.first { !it }
            var last = withFrameMillis { it }
            while (!motion.covered) {
                // 先等到下一帧该出的时候再要一帧：两帧之间主线程和渲染线程都不为背景干活。
                delay(BackgroundMotionPath.FRAME_INTERVAL_MS - VSYNC_SLACK_MS)
                val now = withFrameMillis { it }
                motion.advance(now - last)
                last = now
            }
        }
    }
    return motion
}

/** 等垂直同步最多约一帧（60Hz 16ms），提前这么多开始等，平均下来接近每 50ms 一帧。 */
private const val VSYNC_SLACK_MS = 10L
