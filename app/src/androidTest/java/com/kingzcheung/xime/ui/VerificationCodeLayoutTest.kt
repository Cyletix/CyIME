package com.kingzcheung.xime.ui

import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.kingzcheung.xime.service.VerificationCodeInput
import com.kingzcheung.xime.ui.keyboard.*
import com.kingzcheung.xime.ui.settings.VerificationCodeSettingsCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VerificationCodeLayoutTest {
    @get:Rule val rule = createComposeRule()
    private data class Viewport(val width: Int, val font: Float)

    @Test fun codeActionsRemainReadableScrollableAndClickable() {
        val viewport = mutableStateOf(Viewport(280, 1f))
        val events = mutableListOf<String>()
        rule.setContent {
            val v = viewport.value
            CompositionLocalProvider(LocalDensity provides Density(1f, v.font)) {
                MaterialTheme { Box(Modifier.requiredWidth(v.width.dp).testTag("otp-host")) {
                    CandidateBar(CandidateBarState.ClipboardDisplay(listOf("確認コードは０１２３４５６７です。")),
                        visuals = CandidateBarVisuals(Color.White, Color.Black, Color.Gray),
                        callbacks = CandidateBarCallbacks(onCandidateSelect = { fail("Must not paste full SMS") },
                            onVerificationCodeSelect = { code, sequential -> events += "$code:$sequential" },
                            onOpenClipboard = { events += "original" }))
                } }
            }
        }
        for (v in listOf(Viewport(240, 1f), Viewport(280, 2f), Viewport(360, 1.3f), Viewport(800, 2f))) {
            rule.runOnIdle { viewport.value = v }
            for (tag in listOf("verification-code-paste", "verification-code-digits", "verification-code-original")) {
                val action = rule.onNodeWithTag(tag).performScrollTo()
                rule.assertGeometry("otp-host", "验证码按钮 $v $tag")
                assertTrue(action.fetchSemanticsNode().size.height >= 48)
                if (v.width <= 360) action.performClick()
            }
        }
        assertEquals(3, events.count { it == "01234567:false" })
        assertEquals(3, events.count { it == "01234567:true" })
        assertEquals(3, events.count { it == "original" })
    }

    @Test fun settingsKeepPermissionExplanationAndToggleReachable() {
        val viewport = mutableStateOf(Viewport(280, 2f))
        val checked = mutableStateOf(false)
        rule.setContent {
            val v = viewport.value
            CompositionLocalProvider(LocalDensity provides Density(1f, v.font)) {
                MaterialTheme { Column(Modifier.requiredSize(v.width.dp, 360.dp).testTag("otp-settings-host")
                    .verticalScroll(rememberScrollState())) {
                    VerificationCodeSettingsCard(checked.value, denied = true) { checked.value = it }
                } }
            }
        }
        for (v in listOf(Viewport(240, 2f), Viewport(280, 2f), Viewport(360, 1f))) {
            rule.runOnIdle { viewport.value = v; checked.value = false }
            rule.onNode(hasClickAction() and hasText("新短信验证码自动复制")).performScrollTo().performClick()
            rule.runOnIdle { assertTrue(checked.value) }
            rule.onNodeWithText("未获得短信权限", substring = true).performScrollTo()
            rule.assertGeometry("otp-settings-host", "短信权限设置 $v")
        }
    }

    @Test fun sequentialCommitsFillRealSingleAndSplitEditTexts() {
        lateinit var fields: List<EditText>
        lateinit var container: LinearLayout
        var split = false
        rule.setContent {
            val context = LocalContext.current
            AndroidView(factory = {
                container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                fields = List(8) { index -> EditText(context).apply {
                    id = 2000 + index
                    inputType = InputType.TYPE_CLASS_NUMBER
                    doAfterTextChanged { if (split && text.length == 1 && index + 1 < fields.size) fields[index + 1].requestFocus() }
                    container.addView(this)
                } }
                container
            }, modifier = Modifier.fillMaxSize())
        }
        for (code in listOf("0123", "012345", "0123456", "01234567")) for (mode in listOf(false, true)) {
            rule.runOnIdle {
                split = false
                fields.forEach { it.filters = emptyArray(); it.setText("") }
                split = mode
                if (mode) fields.forEach { it.filters = arrayOf(InputFilter.LengthFilter(1)) }
                fields.first().requestFocus()
            }
            val accepted = runBlocking(Dispatchers.Main) {
                VerificationCodeInput.fill(code, target = {
                    val field = container.findFocus() as? EditText
                    field?.let { VerificationCodeInput.Target("fixture", it.id, it.inputType, it.id.toLong()) }
                }, commit = { digit ->
                    val field = container.findFocus() as? EditText
                    field?.onCreateInputConnection(EditorInfo())?.commitText(digit, 1) == true
                })
            }
            assertTrue("$code split=$mode", accepted)
            rule.runOnIdle {
                assertEquals(code, if (mode) fields.joinToString("") { it.text.toString() } else fields.first().text.toString())
            }
        }
    }
}
