package com.kingzcheung.xime.ui.render

import android.Manifest.permission.RECEIVE_SMS
import android.app.Application
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.settings.VerificationCodeSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-rCN-w360dp-h400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerificationCodePermissionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun grantingInSettingsCompletesRequestedEnableButRespectsExplicitOff() {
        val app = rule.activity.application
        val scenario = rule.activityRule.scenario
        shadowOf(app).denyPermissions(RECEIVE_SMS)
        rule.setContent { MaterialTheme { VerificationCodeSettings() } }
        // 第一个开关是「新短信验证码自动复制」；下面还有「验证码发到其他设备」。
        val toggle = rule.onAllNodes(isToggleable())[0]
        val warning = rule.onNodeWithText("未获得短信权限", substring = true)
        toggle.assertIsOff().performClick()
        rule.runOnIdle {
            val activity = rule.activity
            val request = shadowOf(activity).lastRequestedPermission
            assertArrayEquals(arrayOf(RECEIVE_SMS), request.requestedPermissions)
            activity.onRequestPermissionsResult(request.requestCode, arrayOf(RECEIVE_SMS), intArrayOf(PERMISSION_DENIED))
        }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        warning.assertIsDisplayed()
        toggle.assertIsOff()
        assertFalse(SettingsPreferences.isSmsCodeEnabled(app))
        saveImage("denied")

        scenario.moveToState(Lifecycle.State.CREATED)
        shadowOf(app).grantPermissions(RECEIVE_SMS)
        scenario.moveToState(Lifecycle.State.RESUMED)
        warning.assertDoesNotExist()
        toggle.assertIsOn()
        assertTrue(SettingsPreferences.isSmsCodeEnabled(app))
        saveImage("allowed")

        toggle.performClick()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        warning.assertDoesNotExist()
        toggle.assertIsOff()
        assertFalse(SettingsPreferences.isSmsCodeEnabled(app))
    }

    private fun saveImage(state: String) {
        val out = System.getenv("CYIME_RENDER_OUT") ?: return
        val root = rule.activity.window.decorView
        val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { root.draw(Canvas(image)) }
        File(out, "sms-$state.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
