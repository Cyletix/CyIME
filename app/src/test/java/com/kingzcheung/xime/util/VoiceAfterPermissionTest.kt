package com.kingzcheung.xime.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** After allowing the microphone from the voice key, the keyboard starts voice once, without a second tap. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class VoiceAfterPermissionTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun askFromVoiceKey() {
        PermissionHelper.requestRecordAudioPermission(app)
        shadowOf(app).nextStartedActivity // the permission page
    }

    @Test fun `allowed from the voice key starts voice once`() {
        askFromVoiceKey()
        shadowOf(app).grantPermissions(PermissionHelper.PERMISSION_RECORD_AUDIO)
        PermissionHelper.onRecordAudioResult(true)
        assertTrue(PermissionHelper.takeVoiceAfterGrant(app))
        assertFalse("only once", PermissionHelper.takeVoiceAfterGrant(app))
    }

    @Test fun `denied, stale, or not asked by the voice key never starts voice`() {
        askFromVoiceKey()
        PermissionHelper.onRecordAudioResult(false)
        assertFalse(PermissionHelper.takeVoiceAfterGrant(app))

        PermissionHelper.onRecordAudioResult(true) // a result nobody asked for from the voice key
        assertFalse(PermissionHelper.takeVoiceAfterGrant(app))

        askFromVoiceKey()
        shadowOf(app).grantPermissions(PermissionHelper.PERMISSION_RECORD_AUDIO)
        PermissionHelper.onRecordAudioResult(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(11)) // keyboard came back much later
        assertFalse(PermissionHelper.takeVoiceAfterGrant(app))
    }
}
