package com.kingzcheung.xime.service

import android.util.Log
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * RichTap type1（android.os.DynamicEffect + android.os.HapticPlayer）最小实现。
 * 需要 manifest 声明 <uses-library android:name="richtap-api" android:required="false"/>。
 * 不支持时 available=false，调用方回退现有 Vibrator 逻辑。
 *
 * 基于 issue #15 和 GKME 项目的逆向成果：
 * https://github.com/Cyletix/CyIME/issues/15
 * https://github.com/4zyz4/GKME
 */
object RichTapKeyHaptics {

    private const val TAG = "RichTapKeyHaptics"

    private var createEffect: Method? = null
    private var playerCtor: Constructor<*>? = null
    private var startMethod: Method? = null
    private var stopMethod: Method? = null
    private var player: Any? = null

    @Volatile
    private var attempted = false

    @Volatile
    var available = false
        private set

    // Cache HE JSON strings to avoid repeated JSON construction
    private val heCache = mutableMapOf<String, String>()

    @Synchronized
    fun init(): Boolean {
        if (attempted) return available
        attempted = true
        available = runCatching {
            val hp = Class.forName("android.os.HapticPlayer")
            val hpAvailable = hp.getMethod("isAvailable").invoke(null) as? Boolean ?: false
            if (!hpAvailable) {
                Log.i(TAG, "HapticPlayer.isAvailable() returned false")
                false
            } else {
                val de = Class.forName("android.os.DynamicEffect")
                createEffect = de.getMethod("create", String::class.java)
                playerCtor = hp.getConstructor(de)
                startMethod = hp.getMethod(
                    "start",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                )
                stopMethod = hp.getMethod("stop")
                Log.i(TAG, "RichTap initialized successfully")
                true
            }
        }.getOrElse {
            Log.i(TAG, "RichTap 不可用: ${it.message}")
            false
        }
        return available
    }

    /**
     * 播放一次按键效果。
     * @param strength 0-255；0 时使用默认 200。
     * @return true 表示已交给 RichTap；false 表示调用方应回退系统震动。
     */
    @Synchronized
    fun play(
        type: KeyFeedbackType,
        strength: Int,
        longPress: Boolean = false,
        keyUp: Boolean = false,
    ): Boolean {
        if (!init()) return false
        val create = createEffect ?: return false
        val ctor = playerCtor ?: return false
        val start = startMethod ?: return false
        val spec = KeyHapticSpec.of(type, longPress, keyUp)
        val amp = if (strength == 0) 200 else strength.coerceIn(1, 255)

        return runCatching {
            // type1 先停旧的，避免旧 track 卡住参数；stop 是全局取消，按键场景可接受。
            stopLocked()
            val heJson = getCachedHeJson(spec)
            val effect = create.invoke(null, heJson)
            val p = ctor.newInstance(effect)
            start.invoke(p, 1, 0, amp, spec.frequencyHe)
            player = p
            true
        }.getOrElse {
            Log.w(TAG, "play 失败: ${it.message}")
            false
        }
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        val p = player ?: return
        runCatching { stopMethod?.invoke(p) }
        player = null
    }

    private fun getCachedHeJson(spec: KeyHapticSpec): String {
        val cacheKey = "${spec.durationMs},${spec.frequencyHe},${spec.peak}"
        return heCache.getOrPut(cacheKey) { heClick(spec) }
    }

    /** HE specification for a key click. Visible for testing. */
    data class KeyHapticSpec(
        val durationMs: Int,
        val frequencyHe: Int,
        val peak: Double,
    ) {
        companion object {
            fun of(type: KeyFeedbackType, longPress: Boolean, keyUp: Boolean): KeyHapticSpec = when {
                longPress -> KeyHapticSpec(60, 56, 1.00)
                keyUp -> KeyHapticSpec(18, 62, 0.55)
                type == KeyFeedbackType.CURSOR_STEP -> KeyHapticSpec(16, 64, 0.50)
                type == KeyFeedbackType.HANDWRITING_ERASE -> KeyHapticSpec(18, 62, 0.55)
                type == KeyFeedbackType.DELETE -> KeyHapticSpec(26, 58, 0.90)
                type == KeyFeedbackType.SPACE || type == KeyFeedbackType.ENTER ->
                    KeyHapticSpec(52, 56, 1.00)
                type == KeyFeedbackType.MODIFIER || type == KeyFeedbackType.MODE_SWITCH ->
                    KeyHapticSpec(28, 60, 0.80)
                type == KeyFeedbackType.NAVIGATION -> KeyHapticSpec(20, 62, 0.55)
                type == KeyFeedbackType.TOOLBAR -> KeyHapticSpec(36, 56, 0.95)
                else -> KeyHapticSpec(36, 56, 1.00)
            }
        }
    }

    /** Generate HE 1.0 JSON for a key click. Visible for testing. */
    fun heClick(spec: KeyHapticSpec): String {
        val d = spec.durationMs.coerceIn(20, 5000)
        val t1 = (d * 0.10).toInt().coerceIn(1, d - 3)
        val t2 = (d * 0.40).toInt().coerceIn(t1 + 1, d - 1)
        val peak = spec.peak.coerceIn(0.0, 1.0)
        val mid = (peak * 0.45)
        return "{" +
            "\"Metadata\":{\"Created\":\"cyime\",\"Description\":\"key-click\",\"Version\":1}," +
            "\"Pattern\":[{\"Event\":{" +
            "\"Type\":\"continuous\",\"RelativeTime\":0,\"Duration\":$d," +
            "\"Parameters\":{\"Frequency\":${spec.frequencyHe},\"Intensity\":100,\"Curve\":[" +
            "{\"Frequency\":0.0,\"Intensity\":0.0,\"Time\":0}," +
            "{\"Frequency\":0.0,\"Intensity\":$peak,\"Time\":$t1}," +
            "{\"Frequency\":0.0,\"Intensity\":$mid,\"Time\":$t2}," +
            "{\"Frequency\":0.0,\"Intensity\":0.0,\"Time\":$d}" +
            "]}}}]}"

    }
}
