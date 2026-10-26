package com.kingzcheung.xime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import kotlin.concurrent.thread

/**
 * 开发者工具（只在“装到我的设备”包里起作用），给自动打字和测试脚本用，免得测试学进用户词库：
 * - `op=incognito --ez on true|false`：开关无痕，测试前打开、测试后恢复，Rime 和 CyLM 都不学。
 * - `op=unlearn`：读 Android/data/<包名>/files/unlearn.tsv（每行“词<TAB>次数”），从当前方案的用户词库里
 *   减去这么多次学习，减完的词回到词库原来的位置；用户自己别的学习不动。
 * - `op=reset_cylm`：删掉 CyLM 在本机学到的那层（rime/cylm_learned.tsv），下次打开回到安装包带的用户层。
 *   之后要重启输入法进程，免得内存里的那层再写回去。
 * 用法：adb shell am broadcast -n com.cyletix.cyime/com.kingzcheung.xime.service.DevLearningReceiver -a com.cyletix.cyime.DEV_LEARNING --es op unlearn
 * 只有 adb（持有 DUMP 权限）能发，普通应用发不进来。
 */
class DevLearningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEV_TOOLS || intent.action != ACTION) return
        val app = context.applicationContext
        when (intent.getStringExtra("op")) {
            "incognito" -> {
                val on = intent.getBooleanExtra("on", true)
                SettingsPreferences.setIncognito(app, on)
                FileLogger.i(TAG, "incognito=$on")
            }
            "unlearn" -> {
                val file = File(app.getExternalFilesDir(null), "unlearn.tsv")
                if (!file.isFile) { FileLogger.w(TAG, "unlearn: ${file.path} missing"); return }
                val rows = file.readText()
                val pending = goAsync()
                thread(name = "dev-unlearn") {
                    try {
                        val taken = RimeEngine.getInstance().unlearn(rows)
                        FileLogger.i(TAG, "unlearn: $taken commits taken back")
                    } finally { pending.finish() }
                }
            }
            "reset_cylm" -> {
                val layer = File(File(app.filesDir, "rime"), "cylm_learned.tsv")
                FileLogger.i(TAG, "reset_cylm: ${if (layer.delete()) "removed" else "nothing to remove"}")
            }
        }
    }

    private companion object {
        const val ACTION = "com.cyletix.cyime.DEV_LEARNING"
        const val TAG = "DevLearning"
    }
}
