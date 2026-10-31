package com.kingzcheung.xime.rime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.kingzcheung.xime.util.FileLogger

internal suspend fun retryPreparation(
    status: (String) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
    prepare: suspend () -> Boolean,
) {
    var attempt = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        status("正在自动准备中文输入，请稍候…")
        val error = try { if (prepare()) return else "词库尚未就绪" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // 原来异常只取了一句 message，日志里看不到是哪一步失败（GitHub #36 新装一直卡在准备输入方案）。
            FileLogger.w("AutomaticPreparation", "Preparation attempt ${attempt + 1} failed", failure)
            failure.message ?: "准备失败"
        }
        val seconds = listOf(3L, 10L, 30L, 60L)[attempt.coerceAtMost(3)]
        attempt++
        if (error == "词库尚未就绪") FileLogger.w("AutomaticPreparation", "Preparation attempt $attempt not ready")
        status("$error\n${seconds} 秒后自动重试，无需操作")
        wait(seconds * 1000)
    }
}
