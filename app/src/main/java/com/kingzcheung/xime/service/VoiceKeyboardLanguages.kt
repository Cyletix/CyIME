package com.kingzcheung.xime.service

import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputModes

/** A keyboard pair shares the recording session, never the recognizer's language capabilities. */
internal data class VoiceKeyboardLanguages(
    val language: InputLanguage,
    val nativeLanguage: InputLanguage,
) {
    // Keyboard pairing does not add languages to a recognizer. English always uses the
    // English selection, including the English side of a Japanese keyboard profile.
    val recognitionLanguage: InputLanguage
        get() = language

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
