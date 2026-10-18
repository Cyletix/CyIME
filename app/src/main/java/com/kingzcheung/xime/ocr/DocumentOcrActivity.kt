package com.kingzcheung.xime.ocr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/** Only bridges Android permission/settings focus. Camera and review UI belong to the IME. */
class DocumentOcrActivity : ComponentActivity() {
    companion object {
        private var pending = false
        private var pickingImage = false
        private var host: WeakReference<DocumentOcrActivity>? = null
        fun takePending(): Boolean = if (pickingImage) false else pending.also { pending = false }
        fun finishHost() { host?.get()?.finish(); host = null; OcrGallerySelection.uri.value = null }
        fun finishHiddenHost() { if (!pending && !pickingImage) finishHost() }
        fun open(context: Context) {
            OcrGallerySelection.uri.value = null
            context.startActivity(Intent(context, DocumentOcrActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        fun requestCamera(context: Context) {
            pending = true
            context.startActivity(Intent(context, DocumentOcrActivity::class.java)
                .putExtra("permission_only", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        fun pickImage(context: Context) {
            pending = true
            pickingImage = true
            host?.get()?.let { it.openGallery(); return }
            context.startActivity(Intent(context, DocumentOcrActivity::class.java)
                .putExtra("pick_image", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    private var editor: EditText? = null
    private var opened = false
    private var galleryReturned = false
    private fun openGallery() {
        pickingImage = true
        intent.putExtra("pick_image", true)
        opened = false
        galleryReturned = false
        gallery.launch("image/*")
    }
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickingImage = false
        if (uri != null) OcrGallerySelection.uri.value = uri
        galleryReturned = true
        // A restored Activity/process must also return to OCR, including picker cancellation.
        // Keep the request alive until window focus allows showKeyboard() to run.
        pending = true
        showKeyboard()
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (!allowed) Toast.makeText(this, "未允许相机，可稍后重试", Toast.LENGTH_SHORT).show()
        if (intent.getBooleanExtra("permission_only", false)) { pending = true; finish() }
        else showKeyboard()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryReturned = savedInstanceState?.getBoolean("gallery_returned", false) ?: false
        savedInstanceState?.getString("gallery_uri")?.let { OcrGallerySelection.uri.value = android.net.Uri.parse(it) }
        if (!intent.getBooleanExtra("permission_only", false)) {
            host = WeakReference(this)
            // Transparent focus host for settings; it never draws a second scanner page.
            editor = EditText(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(android.graphics.Color.TRANSPARENT)
                alpha = 0f
                isSingleLine = true
                showSoftInputOnFocus = true
            }
            setContentView(editor, android.view.ViewGroup.LayoutParams(1, 1))
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        if (savedInstanceState == null) {
            if (intent.getBooleanExtra("pick_image", false)) openGallery()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.CAMERA)
            else if (intent.getBooleanExtra("permission_only", false)) { pending = true; finish() }
            else showKeyboard()
        }
    }
    private fun showKeyboard() {
        if (opened) return
        if (intent.getBooleanExtra("pick_image", false) && !window.decorView.hasWindowFocus()) return
        val selected = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        if (!selected.orEmpty().startsWith("$packageName/")) {
            Toast.makeText(this, "请先选择 CyIME 输入法", Toast.LENGTH_SHORT).show(); finish(); return
        }
        pending = true
        opened = true
        editor?.apply { requestFocus(); post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val galleryMode = intent.getBooleanExtra("pick_image", false)
        if (hasFocus && editor != null && (if (galleryMode) galleryReturned else
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED))
            showKeyboard()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("gallery_returned", galleryReturned)
        outState.putString("gallery_uri", OcrGallerySelection.uri.value?.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (isFinishing && intent.getBooleanExtra("pick_image", false) && !galleryReturned) {
            pickingImage = false
            pending = false
        }
        if (host?.get() === this) host = null
        super.onDestroy()
    }
}
