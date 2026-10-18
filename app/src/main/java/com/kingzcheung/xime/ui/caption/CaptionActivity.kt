package com.kingzcheung.xime.ui.caption

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kingzcheung.xime.util.PermissionHelper

/**
 * Full-screen live captions for a hard-of-hearing reader: one row per speaker, newest speech at
 * the right. Opened from its own home-screen icon, the quick-settings tile or the keyboard menu.
 */
class CaptionActivity : ComponentActivity() {
    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, CaptionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private lateinit var controller: CaptionController
    private var permissionAsked = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) controller.start()
        else Toast.makeText(this, "没有麦克风权限就听不到，请在手机设置里允许", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Full screen without the status bar; the navigation bar stays so "back" is always visible
        // (hiding it made the swipe that reveals it land on the bottom buttons). Never dims while open.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = CaptionController(this)
        setContent { CaptionScreen(controller, onExit = { finish() }) }
    }

    override fun onStart() {
        super.onStart()
        controller.refreshCallCaption()
        if (PermissionHelper.hasRecordAudioPermission(this)) {
            if (controller.status == CaptionController.Status.Idle || controller.resumeOnForeground) {
                controller.resumeOnForeground = false
                controller.start()
            }
        } else if (!permissionAsked) {
            permissionAsked = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        // Background: release the microphone (no foreground service in this version); resume on return.
        if (controller.status == CaptionController.Status.Listening || controller.status == CaptionController.Status.Starting) {
            controller.resumeOnForeground = true
            controller.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
