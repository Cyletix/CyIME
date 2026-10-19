package com.kingzcheung.xime.plugin

import com.kingzcheung.xime.plugin.core.lua.QuickSendHostApi
import com.kingzcheung.xime.plugin.core.lua.QuickSendItem

/**
 * 保留旧插件协议名 `quick_send_read`，但固定剪贴板（原快捷发送）一律不对插件开放：
 * 固定内容多是账号、密码、地址等个人信息，任何插件、任何权限声明都读不到。
 * 已安装的旧插件调用 host.quickSend.list() 只会拿到空列表，不会报错。
 */
class QuickSendHostApiImpl : QuickSendHostApi {
    override fun list(): List<QuickSendItem> = emptyList()
}
