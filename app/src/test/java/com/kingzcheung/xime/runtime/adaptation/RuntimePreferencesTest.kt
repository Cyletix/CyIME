package com.kingzcheung.xime.runtime.adaptation

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class RuntimePreferencesTest {
    private fun prefs(values: Map<String, Any>): SharedPreferences = mock<SharedPreferences>().also { prefs ->
        whenever(prefs.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
        whenever(prefs.getBoolean(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1) }
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
    }

    private fun read(settings: Map<String, Any>, asrValues: Map<String, Any> = emptyMap()): RuntimeRequests {
        val context = mock<Context>()
        val settingsPrefs = prefs(settings)
        val asrPrefs = prefs(asrValues)
        whenever(context.getSharedPreferences(eq("kime_settings"), any())).thenReturn(settingsPrefs)
        whenever(context.getSharedPreferences(eq("asr_model"), any())).thenReturn(asrPrefs)
        return RuntimePreferences.read(context).also {
            verify(settingsPrefs, never()).edit()
            verify(asrPrefs, never()).edit()
        }
    }

    @Test fun `missing values use current defaults without inventing persisted auto mode`() {
        val requests = read(emptyMap())
        assertEquals(RuntimeRequest(AnimationLevel.OFF, PreferenceOrigin.CURRENT_DEFAULT), requests.animation)
        assertEquals(RuntimeRequest(false, PreferenceOrigin.CURRENT_DEFAULT), requests.keyGlow)
        assertEquals(RuntimeRequest(true, PreferenceOrigin.CURRENT_DEFAULT), requests.neuralPrediction)
        assertEquals(RuntimeRequest(false, PreferenceOrigin.CURRENT_DEFAULT), requests.voiceCorrection)
    }

    @Test fun `legacy values even equal to defaults retain unknown provenance`() {
        val requests = read(mapOf("key_glow_enabled" to false, "smart_prediction_enabled" to true),
            mapOf("selected_model" to SpeechModelCatalog.ZIPFORMER_TWO_PASS))
        assertEquals(PreferenceOrigin.SAVED_UNATTRIBUTED, requests.animation.origin)
        assertEquals(PreferenceOrigin.SAVED_UNATTRIBUTED, requests.neuralPrediction.origin)
        assertEquals(RuntimeRequest(true, PreferenceOrigin.SAVED_UNATTRIBUTED), requests.voiceCorrection)
    }

    @Test fun `automatic voice default is observed and manual override wins`() {
        val automatic = mapOf("device_default_voice_model" to SpeechModelCatalog.ZIPFORMER_TWO_PASS)
        assertTrue(read(automatic).voiceCorrection.value)
        assertFalse(read(automatic, mapOf("selected_model" to SpeechModelCatalog.ZIPFORMER)).voiceCorrection.value)
    }

    @Test fun `motion and glow are read independently with read only legacy fallback`() {
        for (glow in listOf(false, true)) for (animation in listOf(false, true)) {
            val requests = read(mapOf("key_glow_enabled" to glow, "key_animation_enabled" to animation))
            assertEquals(glow, requests.keyGlow.value)
            assertEquals(if (animation) AnimationLevel.STANDARD else AnimationLevel.OFF, requests.animation.value)
            assertEquals(PreferenceOrigin.SAVED_UNATTRIBUTED, requests.animation.origin)
        }
        val legacy = read(mapOf("key_glow_enabled" to true))
        assertEquals(AnimationLevel.STANDARD, legacy.animation.value)
        assertTrue(legacy.keyGlow.value)
    }

    @Test fun `disabled speech or online mode never requests local correction`() {
        val asr = mapOf("selected_model" to SpeechModelCatalog.TWO_PASS)
        assertFalse(read(mapOf("stt_enabled" to false), asr).voiceCorrection.value)
        assertFalse(read(mapOf("stt_use_local" to false), asr).voiceCorrection.value)
        assertFalse(read(mapOf("stt_online_plugin_id" to "configured"), asr).voiceCorrection.value)
        assertFalse(read(mapOf("smart_prediction_enabled" to false)).neuralPrediction.value)
    }
}
