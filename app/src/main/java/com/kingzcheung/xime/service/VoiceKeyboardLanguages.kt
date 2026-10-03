package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputModes

/** Keyboard editing may use English without changing an ongoing recognizer's language. */
internal data class VoiceKeyboardLanguages(
    val language: InputLanguage,
    val nativeLanguage: InputLanguage,
) {
    fun accepts(next: VoiceKeyboardLanguages): Boolean {
        if (nativeLanguage != next.nativeLanguage) return false
        if (language == next.language) return true
        if (nativeLanguage == InputLanguage.UNSPECIFIED || nativeLanguage == InputLanguage.ENGLISH) return false
        return (language == nativeLanguage || language == InputLanguage.ENGLISH) &&
            (next.language == nativeLanguage || next.language == InputLanguage.ENGLISH)
    }
}

internal val InputUIState.voiceKeyboardLanguages: VoiceKeyboardLanguages
    get() = VoiceKeyboardLanguages(inputProfile.language, InputModes.languageOf(currentSchemaId, schemas))
