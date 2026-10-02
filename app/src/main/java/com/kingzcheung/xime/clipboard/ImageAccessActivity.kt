package com.kingzcheung.xime.clipboard

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Permission and picker UI runs in an Activity, never steals focus inside the IME window. */
class ImageAccessActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val images = ClipboardImages.getInstance(this)
        images.enableRecentPhotos(images.hasPhotoAccess())
        finish()
    }
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) finish()
        else lifecycleScope.launch {
            runCatching { ClipboardImages.getInstance(this@ImageAccessActivity).prepare(
                ClipboardImage(uri.toString(), "image/png", System.currentTimeMillis())) }
                .onFailure { android.widget.Toast.makeText(this@ImageAccessActivity, "无法读取图片，请重新选择（最大 32 MiB）", android.widget.Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (intent.getBooleanExtra("pick_image", false)) picker.launch("image/*")
        else permission.launch(when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        })
    }
}
