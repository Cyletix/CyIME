package com.kingzcheung.xime.audit

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kingzcheung.xime.service.CandidateState
import com.kingzcheung.xime.service.PredictionManager
import com.kingzcheung.xime.service.TrackedInputConnection
import com.kingzcheung.xime.service.commitEnglishCompletion
import com.kingzcheung.xime.service.commitAssociationSelection
import com.kingzcheung.xime.service.commitEnglishLetter
import com.kingzcheung.xime.service.englishSentenceStart
import com.kingzcheung.xime.settings.CellDictionaryManager
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.settings.LayoutInputCategory
import com.kingzcheung.xime.ui.settings.LayoutInputSettingsContent
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel

/** Called after tapping the real settings row. Writes only to this process's EditText. */
fun verifyEnglishEditor(app: Application, enabled: Boolean) {
    check(KeyboardInputPreferences.read(app).englishSentenceCaps == enabled)
    val editor = EditText(app)
    editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    val connection = checkNotNull(editor.onCreateInputConnection(EditorInfo()))
    fun type(source: String, position: Int, key: String, expected: String, shift: Boolean = false) {
        editor.setText(source)
        editor.setSelection(position)
        val upper = shift || englishSentenceStart(app, connection)
        val state = commitEnglishLetter(CandidateState(), key, upper, { true }) { connection.commitText(it, 1) }
        check(state != null && editor.text.toString() == expected) {
            "At $position in '$source', expected '$expected', got '${editor.text}'"
        }
    }
    type("", 0, "h", if (enabled) "H" else "h")
    type("Hello. ", 7, "w", if (enabled) "Hello. W" else "Hello. w")
    type("Hi\n", 3, "h", if (enabled) "Hi\nH" else "Hi\nh")
    type("Hello ", 6, "w", "Hello w")
    type("hello", 2, "a", "heallo")
    type("hello", 0, "a", if (enabled) "Ahello" else "ahello")
    type("in ", 3, "h", "in H", shift = true)
    println("EditText sentence caps=$enabled: empty, sentence, newline, word, moved cursor and Shift passed")
}

/** Run the literal-key and candidate-selection paths on the device's real editable text and caret. */
fun verifyEnglishCompletions(app: Application) {
    val editor = EditText(app)
    val writes = mutableListOf<String>()
    val host = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
            error("A prefix completion tried to delete the existing word")
        override fun setSelection(start: Int, end: Int): Boolean =
            error("A prefix completion tried to reposition the caret")
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            check(newCursorPosition == 1)
            writes += text.toString()
            return super.commitText(text, newCursorPosition)
        }
    }
    val connection = TrackedInputConnection(host)
    fun typeAndPick(left: String, right: String, prefix: String, word: String, space: String = "") {
        editor.setText(left + right)
        editor.setSelection(left.length)
        var state = CandidateState()
        for (letter in prefix) state = checkNotNull(commitEnglishLetter(state, letter.toString(), letter.isUpperCase(), { true }) {
            connection.commitText(it, 1)
        })
        state = state.copy(associationCandidates = listOf(word))
        writes.clear()
        check(commitAssociationSelection(state, 1, { error("unexpected whole-word commit") },
            connection::commitEnglishCompletion, suffix = space))
        check(writes == listOf(word.substring(prefix.length) + space)) { "writes=$writes" }
        val expected = left + word + space
        check(editor.text.toString() == expected + right)
        check(editor.selectionStart == expected.length && editor.selectionEnd == expected.length)
        connection.commitText("x", 1)
        check(editor.text.toString() == expected + "x" + right && editor.selectionEnd == expected.length + 1)
        println("PASS '$prefix' -> '$word$space', next letter after completion, right context '$right'")
    }
    typeAndPick("", "", "a", "and")
    typeAndPick("", "", "t", "the")
    typeAndPick("前", "后", "th", "the")
    typeAndPick("😀", "后", "An", "And", " ")

    editor.setText("at")
    editor.setSelection(2)
    connection.memory.started(2, 2, true)
    check(connection.getTextBeforeCursor(1, 0).toString() == "t")
    editor.setSelection(1) // Host moved before its selection callback reached CyIME.
    writes.clear()
    check(!connection.commitEnglishCompletion("t", "the"))
    editor.setSelection(1, 2)
    check(!connection.commitEnglishCompletion("a", "and"))
    check(writes.isEmpty() && editor.text.toString() == "at")
    check(editor.selectionStart == 1 && editor.selectionEnd == 2)
    println("PASS stale cached prefix and active selection leave the text and selection intact")

    val correction = object : InputConnectionWrapper(editor.onCreateInputConnection(EditorInfo()), false) {
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) =
            error("A spelling correction tried to delete the draft before writing")
    }
    editor.setText("前im后")
    editor.setSelection(3)
    check(correction.commitEnglishCompletion("im", "I'm "))
    check(editor.text.toString() == "前I'm 后" && editor.selectionStart == 5 && editor.selectionEnd == 5)
    println("PASS im -> I'm with automatic space in the middle of a draft")
}

@Composable
internal fun InputOptionsScene(app: Application, record: (String) -> Unit, mode: String) {
    val vm = remember {
        KeyboardInputPreferences(bottomPunctuationSwipeUp = !mode.endsWith("legacy"),
            reverseSymbolSwipe = mode.endsWith("reversed"), syllableSplitKey = true,
            symbolInputMode = if (mode.endsWith("long")) SymbolInputMode.LONG_PRESS else SymbolInputMode.SWIPE_UP
        ).save(app)
        KeysConfigHelper.loadConfig(app)
        KeyboardThemes.initFromConfig(app)
        AppFonts.initialize(app)
        KeyboardViewModel(app).also { it.dispatch(KeyboardDispatchAction.InputSessionStarted(false, "rime_ice")) }
    }
    XimeTheme(darkTheme = false) {
        if (mode.endsWith("settings")) {
            LayoutInputSettingsContent(LayoutInputCategory.Qwerty) {}
        } else {
            KeyboardView(vm, KeyboardUiState(isFloatingMode = true, keyboardWidthDp = 320, keyboardHeightDp = 244,
                floatingOffsetY = 80, floatingMinOffsetY = 0, currentSchemaId = "rime_ice"),
                KeyboardCallbacks(onKeyPress = { key, _ -> record(key) }, onCandidateSelect = {}, onCommitExactText = record),
                inputSessionManagedByHost = true)
        }
    }
    if (mode.endsWith("default")) LaunchedEffect(Unit) {
        val prediction = PredictionManager(app, this, {})
        val store = CellDictionaryManager.store(app)
        val entry = store.install("cyimequokka\n".toByteArray())
        suspend fun found() = "cyimequokka" in prediction.getEnglishAssociations("cyimequok")
        check(!found())
        store.select(entry.id, true)
        store.selectEnglish(true)
        store.markApplied(setOf(entry.id), true)
        check(found())
        store.select(entry.id, false)
        store.markApplied(emptySet(), true)
        check(!found())
        check("hello" in prediction.getEnglishAssociations("hel"))
        record("dictionary-ok")
    }
}
