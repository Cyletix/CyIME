package com.kingzcheung.xime.service

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class EditorSessionInputPolicyTest {
    private fun editor(type: Int, id: Int = 1) = EditorInfo().apply {
        inputType = type
        fieldId = id
        packageName = "fixture.invalid"
    }

    @Test fun allPasswordVariationsLockDirectAsciiWithoutSavingLanguage() {
        val types = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        types.forEach { type ->
            val session = EditorSessionInputPolicy()
            val entered = session.begin(editor(type), false, false, "original-layout")
            assertTrue(entered.isAsciiMode)
            assertTrue(session.directInput)
            assertTrue(session.locksNativeInput)
            assertFalse(session.choose(false, "other-layout"))
            assertNull(session.schemaOverride)
            assertTrue(session.asciiOverride == true)
            val restored = session.begin(editor(InputType.TYPE_CLASS_TEXT, 2), false, true, "original-layout")
            assertFalse(restored.isAsciiMode)
            assertEquals("original-layout", restored.currentSchemaId)
            assertFalse(session.scoped)
            assertNull(session.asciiOverride)
            assertFalse(session.engineAsciiOverride()!!)
            session.completePreparation()
            assertNull(session.engineAsciiOverride())
        }
    }

    @Test fun numberPasswordIsDigitsEvenWhenSignedAndDecimalFlagsArePresent() {
        val policy = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_VARIATION_PASSWORD or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        assertTrue(policy.numberPanel)
        assertEquals("42", policy.typedLiteral("４２"))
        listOf("+", "-", ".", "a", "中").forEach { assertTrue(policy.typedLiteral(it).isEmpty()) }
    }

    @Test fun numericFlagsAreIndependentAndDoNotEnableChineseOrCalculatorExpressions() {
        for (signed in listOf(false, true)) for (decimal in listOf(false, true)) {
            val policy = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_NUMBER or
                (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0) or
                (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0))
            assertTrue(policy.numberPanel)
            assertTrue(policy.locksNativeInput)
            assertEquals("7", policy.typedLiteral("7"))
            assertEquals(signed, policy.typedLiteral("-").isNotEmpty())
            assertEquals(decimal, policy.typedLiteral(".").isNotEmpty())
            assertTrue(policy.typedLiteral("*").isEmpty())
            assertTrue(policy.typedLiteral("中").isEmpty())
        }
    }

    @Test fun phoneAndDateTimeUseTheirOwnPunctuation() {
        val phone = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_PHONE)
        val date = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        val time = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        val dateTime = EditorFieldPolicy.fromInputType(InputType.TYPE_CLASS_DATETIME)
        assertEquals("+*#,;", phone.typedLiteral("+*#,;"))
        assertEquals("/-.", date.typedLiteral("/-."))
        assertTrue(date.typedLiteral(":").isEmpty())
        assertEquals(":. PM", time.typedLiteral(":. PM"))
        assertTrue(time.typedLiteral("/").isEmpty())
        assertEquals("/-:", dateTime.typedLiteral("/-:"))
        listOf(phone, date, time, dateTime).forEach {
            assertTrue(it.numberPanel)
            assertTrue(it.locksNativeInput)
        }
    }

    @Test fun emailAndUriStartAsciiButExplicitNativeSelectionSurvivesRestartAndLeavesOriginalUntouched() {
        listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI).forEach { variation ->
            val session = EditorSessionInputPolicy()
            val info = editor(InputType.TYPE_CLASS_TEXT or variation)
            assertTrue(session.begin(info, false, false, "original-layout").isAsciiMode)
            assertFalse(session.locksNativeInput)
            assertTrue(session.choose(false, "international-layout"))
            assertFalse(session.directInput)
            val restarted = session.begin(info, true, false, "international-layout")
            assertFalse(restarted.isAsciiMode)
            assertEquals("international-layout", restarted.currentSchemaId)
            val restored = session.begin(editor(InputType.TYPE_CLASS_TEXT, 2), false, false, "international-layout")
            assertFalse(restored.isAsciiMode)
            assertEquals("original-layout", restored.currentSchemaId)
            assertNull(session.schemaOverride)
        }
    }

    @Test fun movingBetweenConstrainedFieldsDoesNotReplaceOriginalProfileWithTemporaryEnglish() {
        val session = EditorSessionInputPolicy()
        session.begin(editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD), false, false, "original")
        session.begin(editor(InputType.TYPE_CLASS_PHONE, 2), false, true, "temporary")
        val restored = session.begin(editor(InputType.TYPE_CLASS_TEXT, 3), false, true, "temporary")
        assertFalse(restored.isAsciiMode)
        assertEquals("original", restored.currentSchemaId)
    }

    @Test fun ordinaryNoSuggestionsSearchAndRawTerminalsDoNotForceEnglish() {
        listOf(InputType.TYPE_NULL, InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).forEach { type ->
            val session = EditorSessionInputPolicy()
            assertFalse(session.begin(editor(type), false, false, "original").isAsciiMode)
            assertFalse(session.scoped)
            assertFalse(session.directInput)
        }
    }

    @Test fun onlyTypedKeysAreNormalizedAndNativeWordsAreNeverPartiallyInserted() {
        val policy = EditorFieldPolicy(EditorFieldPolicy.Kind.PASSWORD)
        // Individual fictional key samples only; no password or host content is retained by the policy.
        assertEquals("A", policy.typedLiteral("Ａ"))
        assertEquals("@", policy.typedLiteral("＠"))
        assertEquals(".", policy.typedLiteral("。"))
        assertEquals("\"", policy.typedLiteral("“"))
        assertEquals(" ", policy.typedLiteral("\u3000"))
        assertTrue(policy.typedLiteral("a中").isEmpty())
        assertTrue(policy.typedLiteral("\n").isEmpty())
    }
}
