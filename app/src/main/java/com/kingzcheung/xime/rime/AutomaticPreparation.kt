package com.kingzcheung.xime.rime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.util.FileLogger
import java.io.IOException

/**
 * 键盘上「正在准备…」说的是这次真正在准备的东西。以前一律写「中文输入」，
 * 只开了德语、法语这些新语言、中文早就编译好时也这样提示（2026-11-02 平板上加了一堆语言后看到）。
 * [missing] 是还没编译出来的启用方案所属的语言；[firstTime] 表示还没有任何编译结果（新装、清除数据）。
 */
internal fun preparingMessage(missing: Collection<InputLanguage>, firstTime: Boolean): String {
    if (firstTime || InputLanguage.CHINESE in missing) return "正在自动准备中文输入，请稍候…"
    val names = missing.filter { it != InputLanguage.ENGLISH && it != InputLanguage.UNSPECIFIED }
        .distinct().map { it.displayName }
    return if (names.isEmpty()) "正在更新输入方案，请稍候…" else "正在准备${names.joinToString("、")}输入，请稍候…"
}

/**
 * GitHub #283（一加 13、Android 17）：读安装包里的方案和模型文件时系统抛出不带说明的 IOException，
 * 提示只剩一行「3 秒后自动重试，无需操作」，重试多少次都一样。读不出安装包要直接说出来，并让用户重新安装。
 */
internal const val UNREADABLE_PACKAGE = "读不出安装包里的文件，请重新下载安装包后卸载重装"

internal fun isPackageReadFailure(failure: Throwable): Boolean =
    failure is IOException && failure.stackTrace.take(4).any { it.className == "android.content.res.AssetManager" }

internal fun preparationFailureText(failure: Throwable): String = when {
    isPackageReadFailure(failure) -> UNREADABLE_PACKAGE
    else -> failure.message?.takeIf { it.isNotBlank() } ?: "准备失败（${failure.javaClass.simpleName}）"
}

internal suspend fun retryPreparation(
    status: (String) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
    problem: () -> String = { "" },
    preparing: () -> String = { "正在自动准备中文输入，请稍候…" },
    prepare: suspend () -> Boolean,
) {
    var attempt = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        status(preparing())
        var userAction = false
        val error = try { if (prepare()) return else problem().ifBlank { "词库尚未就绪" } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // 原来异常只取了一句 message，日志里看不到是哪一步失败（GitHub #36 新装一直卡在准备输入方案）。
            FileLogger.w("AutomaticPreparation", "Preparation attempt ${attempt + 1} failed", failure)
            userAction = isPackageReadFailure(failure)
            preparationFailureText(failure)
        }
        val seconds = listOf(3L, 10L, 30L, 60L)[attempt.coerceAtMost(3)]
        attempt++
        if (error == "词库尚未就绪") FileLogger.w("AutomaticPreparation", "Preparation attempt $attempt not ready")
        status(if (userAction) "$error\n${seconds} 秒后再试一次" else "$error\n${seconds} 秒后自动重试，无需操作")
        wait(seconds * 1000)
    }
}
