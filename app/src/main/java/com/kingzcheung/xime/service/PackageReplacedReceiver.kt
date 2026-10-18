package com.kingzcheung.xime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kingzcheung.xime.util.FileLogger

/**
 * 应用更新后系统发来 MY_PACKAGE_REPLACED：拉起进程即可，XimeApplication.onCreate 会在后台
 * 复制内置词库并编译。这样换了词库的新版本装完就开始编译，用户第一次弹键盘时不必再等
 * 整段“正在加载词库”（平板上全量编译约 40 秒）。
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        FileLogger.i("PackageReplaced", "App updated, dictionary preparation started in background")
    }
}
