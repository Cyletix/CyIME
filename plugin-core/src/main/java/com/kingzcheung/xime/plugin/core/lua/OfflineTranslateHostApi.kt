package com.kingzcheung.xime.plugin.core.lua

/**
 * 宿主离线翻译（Lua `host.translate`）：手机上用下载的翻译模型（Firefox 翻译模型，slimt 引擎）翻，不联网。
 *
 * 权限门禁：仅当插件 manifest 声明 `capabilities.offline_translate: true` 时注入，未声明 `host.translate` 不存在。
 * 宿主只提供「查状态、下载模型、翻一段字」原语；什么时候用离线、什么时候用接口由插件决定。
 * 语言用两个字母的代码（zh、en）；没有的方向 [status] 返回 [STATE_UNSUPPORTED]。
 */
interface OfflineTranslateHostApi {

    /** 这个方向现在能不能用；没有的方向、这台手机的 CPU 跑不了都是 [STATE_UNSUPPORTED]。 */
    fun status(from: String, to: String): OfflineTranslateStatus

    /** 后台下载这个方向的模型（已在下就不重复）。立即返回：null = 已开始或已装好，字符串 = 不能下载的原因。 */
    fun download(from: String, to: String): String?

    /**
     * 后台翻一段字，立即返回请求 id（>= 0）；失败返回 -1（原因经 listener.onError 交付）。
     * 新请求进来时还没开始的旧请求直接丢掉（翻译栏只要最后一句）。
     */
    fun translate(text: String, from: String, to: String, listener: OfflineTranslateListener): Int

    /** 不再要这个请求的结果（幂等）；正在翻的一句翻完也不回调。 */
    fun cancel(id: Int)

    companion object {
        const val STATE_READY = "ready"
        const val STATE_MISSING = "missing"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_UNSUPPORTED = "unsupported"
    }
}

/**
 * @param state [OfflineTranslateHostApi.STATE_READY] 等
 * @param progress 下载中 0–100，其他状态 -1
 * @param sizeMb 这个方向要下载的大小（MB，四舍五入），不支持时 0
 */
data class OfflineTranslateStatus(val state: String, val progress: Int = -1, val sizeMb: Int = 0)

/** 离线翻译结果回调（宿主后台线程调用）。 */
interface OfflineTranslateListener {
    fun onDone(text: String)
    fun onError(message: String)
}
