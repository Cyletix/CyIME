package com.kingzcheung.xime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import kotlin.concurrent.thread

/**
 * 开发者工具（只在“装到我的设备”包里起作用），给自动打字和测试脚本用，免得测试学进用户词库：
 * - `op=incognito --ez on true|false`：开关无痕，测试前打开、测试后恢复，Rime 和 CyLM 都不学。
 * - `op=unlearn`：读 Android/data/<包名>/files/unlearn.tsv（每行“词<TAB>次数”），从当前方案的用户词库里
 *   减去这么多次学习，减完的词回到词库原来的位置；用户自己别的学习不动。
 * - `op=reset_cylm`：删掉 CyLM 在本机学到的那层（rime/cylm_learned.tsv），下次打开回到安装包带的用户层。
 *   之后要重启输入法进程，免得内存里的那层再写回去。
 * - `op=export`：把当前方案的用户词库（Rime 文本快照 .userdb.txt）和 CyLM 学习层复制到
 *   Android/data/<包名>/files/cyime-dev/<时间>/，供电脑 adb pull 分析。
 * 每个 op 的结果写进广播结果（am broadcast 会打印 data=...）和 logcat 标签 DevLearning。
 * 用法：adb shell am broadcast -n com.cyletix.cyime/com.kingzcheung.xime.service.DevLearningReceiver -a com.cyletix.cyime.DEV_LEARNING --es op unlearn
 * 只有 adb（持有 DUMP 权限）能发，普通应用发不进来。
 */
class DevLearningReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (!BuildConfig.DEV_TOOLS) { report("dev tools off in this build"); return }
        val app = context.applicationContext
        when (intent.getStringExtra("op")) {
            "incognito" -> {
                SettingsPreferences.setIncognito(app, intent.getBooleanExtra("on", true))
                // `am broadcast` prints this, so a script can check the switch really changed.
                report("incognito=${SettingsPreferences.isIncognito(app)}")
            }
            "unlearn" -> {
                val file = File(app.getExternalFilesDir(null), "unlearn.tsv")
                if (!file.isFile) { report("unlearn: ${file.path} missing"); return }
                val rows = file.readText()
                background { "unlearn: ${RimeEngine.getInstance().unlearn(rows)} commits taken back" }
            }
            "reset_cylm" -> {
                val layer = File(File(app.filesDir, "rime"), "cylm_learned.tsv")
                report("reset_cylm: ${if (layer.delete()) "removed" else "nothing to remove"}")
            }
            "export" -> {
                val dir = File(app.getExternalFilesDir(null), "cyime-dev/" +
                    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date()))
                background {
                    dir.mkdirs()
                    val rime = File(app.filesDir, "rime")
                    val dict = RimeEngine.getInstance().exportUserDict(File(dir, "user_dict.userdb.txt").path)
                    val cylm = File(rime, "cylm_learned.tsv").takeIf { it.isFile }?.copyTo(File(dir, "cylm_learned.tsv"), true) != null
                    "export: ${dir.path} userdb=$dict cylm_learned=$cylm"
                }
            }
            else -> report("unknown op")
        }
    }

    private fun report(message: String) {
        Log.i(TAG, message)
        resultData = message
    }

    /** Engine work off the main thread; the broadcast result carries its message. */
    private fun background(work: () -> String) {
        val pending = goAsync()
        thread(name = "dev-learning") {
            val message = runCatching(work).getOrElse { "failed: ${it.message}" }
            Log.i(TAG, message)
            pending.resultData = message
            pending.finish()
        }
    }

    private companion object {
        const val ACTION = "com.cyletix.cyime.DEV_LEARNING"
        const val TAG = "DevLearning"
    }
}
