package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HardwareKeyboardPreferencesTest {
    private val context = mock<Context>()
    private val prefs = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()
    private val values = mutableMapOf<String, Boolean>()

    @Before fun setUp() {
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getBoolean(any(), any())).thenAnswer {
            values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        whenever(editor.putBoolean(any(), any())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Boolean>(1)
            editor
        }
    }

    @Test fun existingUsersKeepAllPagingGroupsWithoutWritingPreferences() {
        values[HardwareKeyboardPreferences.PREFIX + "shift_tap"] = false
        values[HardwareKeyboardPreferences.PREFIX + "follow_cursor"] = false
        val original = values.toMap()

        assertEquals(HardwareKeyboardOptions(shiftTap = false, followCursor = false),
            HardwareKeyboardPreferences.read(context))
        assertEquals(original, values)
    }

    @Test fun eachPagingChoicePersistsIndependentlyAndDoesNotChangeShortcuts() {
        for (mask in 0..7) {
            HardwareKeyboardPreferences.set(context, "page_minus_equals", mask and 1 != 0)
            HardwareKeyboardPreferences.set(context, "page_brackets", mask and 2 != 0)
            HardwareKeyboardPreferences.set(context, "page_comma_period", mask and 4 != 0)
            assertEquals(HardwareKeyboardOptions(
                pageMinusEquals = mask and 1 != 0,
                pageBrackets = mask and 2 != 0,
                pageCommaPeriod = mask and 4 != 0,
            ), HardwareKeyboardPreferences.read(context))
        }
        assertEquals(3, values.size)
    }
}
