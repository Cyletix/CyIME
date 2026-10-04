package com.kingzcheung.xime.plugin

import android.content.Context
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.plugin.core.lua.QuickSendHostApi
import com.kingzcheung.xime.plugin.core.lua.QuickSendItem

/**
 * 保留旧插件协议名：同步读 [ClipboardManager] 中固定文本及其编码的内存缓存，零 IO。
 * 仅对 manifest 声明 `quick_send_read` 的插件由生命周期管理器注入。
 */
class QuickSendHostApiImpl(context: Context) : QuickSendHostApi {

    private val clipboardManager = ClipboardManager.getInstance(context)

    override fun list(): List<QuickSendItem> {
        return clipboardManager.quickSendItems.value.map {
            QuickSendItem(
                id = it.id,
                text = it.text,
                code = it.code,
                timestamp = it.timestamp,
                isPinned = it.isPinned,
            )
        }
    }
}
