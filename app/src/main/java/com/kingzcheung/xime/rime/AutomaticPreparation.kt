package com.kingzcheung.xime.rime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        catch (failure: Exception) { failure.message ?: "准备失败" }
        val seconds = listOf(3L, 10L, 30L, 60L)[attempt.coerceAtMost(3)]
        attempt++
        status("$error\n${seconds} 秒后自动重试，无需操作")
        wait(seconds * 1000)
    }
}
