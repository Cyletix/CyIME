package com.kingzcheung.xime.service

/**
 * CyIME 写进输入框的组合区（拼音编码回显 / 语音临时文本）。Android 不能查询宿主的组合状态，
 * 由写入点登记；拼音会同时记下文字，宿主重启输入后框架把组合区定格成普通文字时用来核对。
 */
internal class InlineCompositionTracker {
    var active = false
        private set
    /** 最近一次写入的拼音编码；语音临时文本不记文字（null）。 */
    var text: String? = null
        private set

    fun shown(text: String?) { active = true; this.text = text }
    fun ended() { active = false; text = null }

    /** 新的输入连接开始：旧组合区已不可再用，返回其拼音文字供核对。 */
    fun detach(): String? = text.takeIf { active }.also { ended() }
}

internal data class DetachedComposition(val start: Int, val end: Int)

/**
 * 宿主重启输入后，在光标处找回被定格的拼音编码。光标必须是折叠的，且光标前后的文字
 * 恰好能拼回原编码（允许宿主把光标放在编码中间）；拼法不唯一或对不上时返回 null，不碰任何文字。
 */
internal fun locateDetachedComposition(
    text: String?, selStart: Int, selEnd: Int, before: CharSequence?, after: CharSequence?,
): DetachedComposition? {
    if (text.isNullOrEmpty() || selStart < 0 || selStart != selEnd || before == null || after == null) return null
    val split = (0..text.length).filter { k ->
        before.endsWith(text.substring(0, k)) && after.startsWith(text.substring(k))
    }.singleOrNull() ?: return null
    val start = selStart - split
    return if (start < 0) null else DetachedComposition(start, start + text.length)
}
