package com.kingzcheung.xime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.IncognitoState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Emoji, kaomoji and symbol recents follow the same no-learning rule as typing. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecentUsageIncognitoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val saved = Triple(IncognitoState.manual, IncognitoState.sensitiveEditor, IncognitoState.clipboardSearch)

    @After fun restore() {
        IncognitoState.manual = saved.first
        IncognitoState.sensitiveEditor = saved.second
        IncognitoState.clipboardSearch = saved.third
    }

    @Test fun recentsDoNotChangeInIncognitoOrPasswordFields() {
        IncognitoState.manual = false; IncognitoState.sensitiveEditor = false; IncognitoState.clipboardSearch = false
        val key = RecentUsageStore.KEY_RECENT_EMOJIS
        assertEquals(listOf("😀"), RecentUsageStore.record(context, key, "😀"))

        IncognitoState.manual = true
        assertEquals(listOf("😀"), RecentUsageStore.record(context, key, "🙈"))
        IncognitoState.manual = false

        IncognitoState.sensitiveEditor = true
        assertEquals(listOf("😀"), RecentUsageStore.record(context, key, "🔑"))
        IncognitoState.sensitiveEditor = false

        assertEquals(listOf("😀"), RecentUsageStore.get(context, key))
        assertEquals(listOf("👍", "😀"), RecentUsageStore.record(context, key, "👍"))
    }
}
