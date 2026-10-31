package com.kingzcheung.xime.runtime.adaptation

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.KeyEffectPreferences
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelSelection

/** Read-only bridge. P1 neither migrates defaults nor guesses which legacy values were manual. */
object RuntimePreferences {
    fun read(context: Context): RuntimeRequests {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val asr = context.getSharedPreferences("asr_model", Context.MODE_PRIVATE)
        fun origin(store: SharedPreferences, vararg keys: String) =
            if (keys.any(store::contains)) PreferenceOrigin.SAVED_UNATTRIBUTED else PreferenceOrigin.CURRENT_DEFAULT
        val localVoice = SettingsPreferences.isSttEnabled(context) && SettingsPreferences.isSttUseLocal(context)
        val manager = AsrModelManager(context)
        val selections = com.kingzcheung.xime.settings.InputLanguage.speech.map { manager.getSelectedModelId(it) }
        val correctionOrigin = if ((listOf("selected_model") + com.kingzcheung.xime.settings.InputLanguage.speech.map { "selected_model_${it.id}" }).any(asr::contains)) PreferenceOrigin.SAVED_UNATTRIBUTED else
            origin(prefs, SettingsPreferences.KEY_STT_ENABLED, SettingsPreferences.KEY_STT_USE_LOCAL,
                SettingsPreferences.KEY_STT_ONLINE_PLUGIN_ID)
        return RuntimeRequests(
            animation = RuntimeRequest(if (KeyEffectPreferences.animationEnabled(prefs)) AnimationLevel.STANDARD
                else AnimationLevel.OFF, origin(prefs, KeyEffectPreferences.ANIMATION, KeyEffectPreferences.GLOW)),
            neuralPrediction = RuntimeRequest(SettingsPreferences.isSmartPredictionEnabled(context),
                origin(prefs, SettingsPreferences.KEY_SMART_PREDICTION_ENABLED)),
            voiceCorrection = RuntimeRequest(localVoice && selections.any(SpeechModelSelection::hasCorrection), correctionOrigin),
            keyGlow = RuntimeRequest(KeyEffectPreferences.glowEnabled(prefs), origin(prefs, KeyEffectPreferences.GLOW)),
        )
    }
}
