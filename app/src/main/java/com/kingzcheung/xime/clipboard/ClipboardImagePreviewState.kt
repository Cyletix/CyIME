package com.kingzcheung.xime.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 提示只属于一次系统复制事件；相册和历史记录刷新不能创建提示。 */
internal class ClipboardImagePreviewState(lastCopyKey: String?) {
    companion object { const val MAX_AGE_MS = 5 * 60_000L }

    var lastCopyKey: String? = lastCopyKey
        private set
    private var pendingKey: String? = null
    private var presented = false
    private val _preview = MutableStateFlow<ClipboardImage?>(null)
    val preview = _preview.asStateFlow()

    @Synchronized
    fun beginCopy(uri: String, timestamp: Long): String? {
        val key = "$timestamp:$uri"
        if (key == lastCopyKey || key == pendingKey) return null
        pendingKey = key
        presented = false
        _preview.value = null
        return key
    }

    @Synchronized
    fun prepared(key: String, image: ClipboardImage, now: Long) {
        // 读取文件期间可能已收起键盘，或复制了另一张图片；旧结果不能重新弹出。
        if (pendingKey != key) return
        if (image.timestamp <= 0 || now - image.timestamp >= MAX_AGE_MS) {
            dismiss()
        } else {
            _preview.value = image
        }
    }

    @Synchronized
    fun dismiss(uri: String? = null) {
        if (uri != null && _preview.value?.uri != uri) return
        if (pendingKey != null) lastCopyKey = pendingKey
        pendingKey = null
        presented = false
        _preview.value = null
    }

    @Synchronized
    fun presented(uri: String) { if (_preview.value?.uri == uri) presented = true }

    @Synchronized
    fun dismissIfPresented() { if (presented) dismiss() }

    @Synchronized
    fun failed(key: String) { if (pendingKey == key) { pendingKey = null; _preview.value = null } }

    @Synchronized
    fun expire(now: Long) {
        if (_preview.value?.let { now - it.timestamp >= MAX_AGE_MS } == true) dismiss()
    }
}
