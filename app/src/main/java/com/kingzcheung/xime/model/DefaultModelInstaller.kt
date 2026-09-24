package com.kingzcheung.xime.model

import android.content.Context
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.net.ConnectivityManager
import android.net.Network
import java.util.concurrent.atomic.AtomicBoolean

/** 首次打开应用时安装默认模型；与市场共用下载器、版本和目录，不另建模型副本。 */
object DefaultModelInstaller {
    internal val modelIds = listOf("ochwpro", "zipformer-zh-int8")
    private val started = AtomicBoolean(false)
    private val observingNetwork = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun preferences(context: Context) = context.getSharedPreferences("default_models", Context.MODE_PRIVATE)

    internal fun isHandled(context: Context, id: String): Boolean = preferences(context).getBoolean("handled_$id", false)

    internal fun markHandled(context: Context, id: String) {
        if (id in modelIds) preferences(context).edit()
            .putBoolean("handled_$id", true).remove("pending_$id").commit()
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (observingNetwork.compareAndSet(false, true)) {
            runCatching { app.getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { start(app) }
                }) }
        }
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val prefs = preferences(app)
            val pending = modelIds.filterNot { isHandled(app, it) }
            if (pending.isEmpty()) { started.set(false); return@launch }
            try {
                ModelManager.initialize()
                repeat(3) { attempt ->
                    if (attempt > 0) delay(if (attempt == 1) 15_000L else 60_000L)
                    ModelManager.loadFromRemote(app)
                    for (id in pending) {
                        // 前一模型可能下载较久，用户此时删除后续模型应立即从队列退出。
                        if (isHandled(app, id)) continue
                        val model = ModelManager.getModel(id) ?: continue
                        // 下载先写暂存目录，已有完整市场版本可以直接复用，包括非最新版本。
                        val ready = ModelManager.isModelDownloaded(app, model)
                        var complete = ready
                        if (!ready) {
                            prefs.edit().putBoolean("pending_$id", true).commit()
                            ModelManager.downloadModel(app, model, { state ->
                                complete = state is ModelDownloadState.Complete
                            }, onlyIfDefaultPending = true)
                        }
                        if (complete && ModelManager.isModelDownloaded(app, model)) {
                            // 完成后不再自动补回，用户在模型中心删除模型也是有效选择。
                            markHandled(app, id)
                            if (id == "zipformer-zh-int8") {
                                val settings = SettingsPreferences.getPrefsPublic(app)
                                // 只为尚未选择语音后端的新用户设置本地默认值。
                                val editor = settings.edit()
                                if (!settings.contains(SettingsPreferences.KEY_STT_ENABLED)) {
                                    editor.putBoolean(SettingsPreferences.KEY_STT_ENABLED, true)
                                }
                                if (!settings.contains(SettingsPreferences.KEY_STT_USE_LOCAL) &&
                                    SettingsPreferences.getSttOnlinePluginId(app).isBlank()) {
                                    editor.putBoolean(SettingsPreferences.KEY_STT_USE_LOCAL, true)
                                }
                                editor.apply()
                            }
                        }
                    }
                    if (pending.all { isHandled(app, it) }) return@launch
                }
            } catch (error: Exception) {
                FileLogger.w("DefaultModels", "默认模型准备失败，联网或下次启动重试：${error.message}")
            } finally { started.set(false) }
        }
    }
}
