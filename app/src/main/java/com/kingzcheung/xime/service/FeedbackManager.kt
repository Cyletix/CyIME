package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

enum class HapticMode(val value: String) {
    FollowingSystem("following_system"),
    Enabled("enabled"),
    Disabled("disabled");

    companion object {
        fun fromValue(value: String): HapticMode =
            entries.find { it.value == value } ?: FollowingSystem
    }
}

class FeedbackManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var hasAmplitudeControl = false

    private val feedbackScope = CoroutineScope(Dispatchers.Default)

    private var soundEnabled = true
    private var soundVolume = 50
    private var hapticMode = HapticMode.FollowingSystem
    private var hapticOnKeyUp = false
    private var pressDuration = 0L
    private var longPressDuration = 0L
    private var pressAmplitude = 0
    private var longPressAmplitude = 0

    companion object {
        /** 设置页「按键音色」写入的偏好键。 */
        const val KEY_SOUND_STYLE = "key_sound_style"
    }

    /** 按键音色：default（原有）、mahjong（显示名「Rain75 紫薇轴」，用户实录）、crisp（清脆青轴）；只影响普通键、空格、回车、删除。 */
    private var keySoundStyle = "default"

    private fun readKeySoundStyle(): String =
        SettingsPreferences.getPrefsPublic(context).getString(KEY_SOUND_STYLE, "default") ?: "default"

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun initialize() {
        hasAmplitudeControl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.util.Log.i(
                "FeedbackManager",
                "haptics click=${vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)} " +
                    "tick=${vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)} " +
                    "effectTick=${vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_TICK)} amp=$hasAmplitudeControl"
            )
        }
        initSoundPool()
        loadSettings()
        registerPrefsListener()
    }

    fun release() {
        prefsListener?.let {
            SettingsPreferences.getPrefsPublic(context).unregisterOnSharedPreferenceChangeListener(it)
        }
        soundPool?.release()
        soundPool = null
        feedbackScope.cancel()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.let { pool ->
            soundIds["standard"] = pool.load(context, R.raw.kb_key_standard, 1)
            soundIds["delete"] = pool.load(context, R.raw.kb_key_delete, 1)
            soundIds["space"] = pool.load(context, R.raw.kb_key_space, 1)
            soundIds["enter"] = pool.load(context, R.raw.kb_key_enter, 1)
            // 机械音色全部预加载（每个仅几 KB），切换时不用等解码。
            soundIds["mahjong:standard"] = pool.load(context, R.raw.kb_rec_normal, 1)
            soundIds["mahjong:delete"] = pool.load(context, R.raw.kb_rec_backspace, 1)
            soundIds["mahjong:space"] = pool.load(context, R.raw.kb_rec_space, 1)
            soundIds["mahjong:enter"] = pool.load(context, R.raw.kb_rec_enter, 1)
            soundIds["crisp:standard"] = pool.load(context, R.raw.kb_key_crisp_standard, 1)
            soundIds["crisp:delete"] = pool.load(context, R.raw.kb_key_crisp_delete, 1)
            soundIds["crisp:space"] = pool.load(context, R.raw.kb_key_crisp_space, 1)
            soundIds["crisp:enter"] = pool.load(context, R.raw.kb_key_crisp_enter, 1)
            soundIds["voice_start"] = pool.load(context, R.raw.kb_voice_start, 1)
            soundIds["voice_end"] = pool.load(context, R.raw.kb_voice_end, 1)
            soundIds["handwriting_erase"] = pool.load(context, R.raw.kb_handwriting_erase, 1)
        }
    }

    private fun loadSettings() {
        soundEnabled = SettingsPreferences.isSoundEnabled(context)
        soundVolume = SettingsPreferences.getSoundVolume(context)
        keySoundStyle = readKeySoundStyle()
        hapticMode = HapticMode.fromValue(SettingsPreferences.getHapticMode(context))
        hapticOnKeyUp = SettingsPreferences.isHapticOnKeyUp(context)
        pressDuration = SettingsPreferences.getVibrationPressDuration(context).toLong()
        longPressDuration = SettingsPreferences.getVibrationLongPressDuration(context).toLong()
        pressAmplitude = SettingsPreferences.getVibrationPressAmplitude(context)
        longPressAmplitude = SettingsPreferences.getVibrationLongPressAmplitude(context)
    }

    private fun registerPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sound_enabled" -> soundEnabled = SettingsPreferences.isSoundEnabled(context)
                "sound_volume" -> soundVolume = SettingsPreferences.getSoundVolume(context)
                KEY_SOUND_STYLE -> keySoundStyle = readKeySoundStyle()
                "haptic_mode" -> hapticMode = HapticMode.fromValue(SettingsPreferences.getHapticMode(context))
                "haptic_on_keyup" -> hapticOnKeyUp = SettingsPreferences.isHapticOnKeyUp(context)
                "vibration_press_duration" -> pressDuration = SettingsPreferences.getVibrationPressDuration(context).toLong()
                "vibration_long_press_duration" -> longPressDuration = SettingsPreferences.getVibrationLongPressDuration(context).toLong()
                "vibration_press_amplitude" -> pressAmplitude = SettingsPreferences.getVibrationPressAmplitude(context)
                "vibration_long_press_amplitude" -> longPressAmplitude = SettingsPreferences.getVibrationLongPressAmplitude(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun playKeySound(keyType: String = "standard") {
        if (!soundEnabled) return

        val pool = soundPool ?: return
        val soundId = soundIds["$keySoundStyle:$keyType"] ?: soundIds[keyType] ?: soundIds["standard"] ?: return
        val volume = soundVolume / 100f

        pool.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    /** 外接实体键盘按下时调用；只出声，不振动。 */
    fun playHardwareKeySound(event: android.view.KeyEvent) {
        val sound = HardwareKeySound.soundFor(event.keyCode, event.repeatCount,
            event.isCtrlPressed, event.isAltPressed, event.isMetaPressed) ?: return
        playKeySound(sound)
    }

    fun hapticFeedback(view: View, longPress: Boolean = false, keyUp: Boolean = false, type: KeyFeedbackType = KeyFeedbackType.CHARACTER) {
        // 未开启松手反馈时，不必读取系统触觉设置。
        if (keyUp && !hapticOnKeyUp) return
        when (hapticMode) {
            HapticMode.Enabled -> {}
            HapticMode.Disabled -> return
            HapticMode.FollowingSystem -> {
                val systemEnabled = try {
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1
                    ) == 1
                } catch (_: Exception) {
                    true
                }
                if (!systemEnabled) return
            }
        }

        // 自定义时长、振幅只在「启用」模式下显示，跟随系统时不使用残留值。
        val custom = hapticMode == HapticMode.Enabled
        val duration = if (!custom) 0L else if (longPress) longPressDuration else pressDuration
        val amplitude = if (!custom) 0 else if (longPress) longPressAmplitude else pressAmplitude
        val hfc: Int = if (longPress) {
            HapticFeedbackConstants.LONG_PRESS
        } else if (keyUp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.KEYBOARD_RELEASE
        } else if (type == KeyFeedbackType.CURSOR_STEP || type == KeyFeedbackType.HANDWRITING_ERASE) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }

        val light = keyUp || hfc == HapticFeedbackConstants.TEXT_HANDLE_MOVE
        // 线性马达优先：按下和长按是满强度 click，抬起/光标是稍轻的 tick；
        // 时长由马达决定，振幅设置只调强弱。系统默认时不再交给 KEYBOARD_TAP（三星上偏重）。
        val primitive = if (light) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_CLICK
        val defaultScale = if (light) 0.8f else 1f
        if (vibratePrimitive(primitive, amplitude, defaultScale)) return
        if (duration == 0L && amplitude == 0 && vibratePredefined(if (light) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK)) return
        if (duration != 0L) {
            if (hasAmplitudeControl && amplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            if (hapticMode == HapticMode.Enabled) {
                @Suppress("DEPRECATION")
                flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            }
            view.performHapticFeedback(hfc, flags)
        }
    }

    fun performKeyPressEffect(keyType: String = "toolbar", view: View) {
        val type = KeyFeedbackType.fromKey(keyType)
        playKeySound(type.sound)
        hapticFeedback(view, type = type)
    }

    fun performKeyPressDownEffect(key: String, view: View) {
        val type = KeyFeedbackType.fromKey(key)
        playKeySound(type.sound)
        hapticFeedback(view, type = type)
    }


    /** 设备支持组合原语（线性马达）时按原语震动；振幅 0 用 [defaultScale]。不支持时返回 false 交给原路径。 */
    private fun vibratePrimitive(primitive: Int, amplitude: Int, defaultScale: Float = 1f): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (!vibrator.areAllPrimitivesSupported(primitive)) return false
        val scale = if (amplitude != 0) (amplitude / 255f).coerceIn(0.05f, 1f) else defaultScale
        vibrateTouch(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose())
        return true
    }

    /** 不支持原语但马达确认支持预设效果（tick/click）时使用；状态未知时不用，交回系统触感。 */
    private fun vibratePredefined(effect: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (vibrator.areAllEffectsSupported(effect) != Vibrator.VIBRATION_EFFECT_SUPPORT_YES) return false
        vibrateTouch(VibrationEffect.createPredefined(effect))
        return true
    }

    /** 跟随系统时标记为触摸反馈，随系统“触摸互动”强度；强制启用时不受系统开关影响。 */
    private fun vibrateTouch(effect: VibrationEffect) {
        if (hapticMode == HapticMode.FollowingSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect)
        }
    }

    fun performVibration() {
        if (hapticMode == HapticMode.Disabled) return
        if (!vibrator.hasVibrator()) return
        val custom = hapticMode == HapticMode.Enabled
        val pressAmp = if (custom) pressAmplitude else 0
        if (vibratePrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, pressAmp)) return
        val duration = if (custom && pressDuration != 0L) pressDuration else 20L
        val amplitude = if (hasAmplitudeControl && pressAmp != 0) pressAmp else VibrationEffect.DEFAULT_AMPLITUDE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
