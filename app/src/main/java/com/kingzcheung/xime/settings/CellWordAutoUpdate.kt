package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.util.FileLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 词库在线更新（用户 2026-11-01「我维护的词库需要实时更新」）：键盘收起后，每天最多读一次公开的
 * word_updates.json（和「检查内置词库更新」同一个文件、同样的线路），有新版就下载并应用。
 * 只下载，不上传任何内容；断网或下载失败时照常用已有词库，6 小时后再试。
 * 用户在「导入的词库」页还有没点「应用更改」的选择时只下载不应用，免得替他应用别的改动。
 */
object CellWordAutoUpdate {
    private const val TAG = "CellWordAutoUpdate"
    const val KEY_ENABLED = "cell_word_auto_update"
    private const val KEY_CHECKED_AT = "cell_word_auto_update_checked_at"
    internal const val INTERVAL_MS = 24L * 60 * 60 * 1000
    internal const val RETRY_MS = 6L * 60 * 60 * 1000

    // Not the service scope: a deployment that has started must not be cancelled half way when the keyboard goes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    fun isEnabled(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** A clock moved backwards also counts as due, so a wrong date cannot stop updates for good. */
    internal fun due(now: Long, checkedAt: Long): Boolean = now < checkedAt || now - checkedAt >= INTERVAL_MS

    /** Apply only a downloaded word revision, never a cell selection the user has not confirmed. */
    internal fun shouldApply(state: CellDictionaryState): Boolean =
        state.downloadedWordRevision != state.appliedWordRevision &&
            state.selected == state.applied && state.selectedEnglish == state.appliedEnglish

    /** Called when the keyboard hides. Returns at once; nothing runs while the user types. */
    fun onKeyboardHidden(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val prefs = SettingsPreferences.getPrefsPublic(app)
        val now = System.currentTimeMillis()
        if (!due(now, prefs.getLong(KEY_CHECKED_AT, 0L))) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                // Recorded first: a failed check is retried after RETRY_MS, not on every hide.
                prefs.edit().putLong(KEY_CHECKED_AT, now - INTERVAL_MS + RETRY_MS).apply()
                CellDictionaryManager.checkWordUpdates(app)
                prefs.edit().putLong(KEY_CHECKED_AT, now).apply()
                if (shouldApply(CellDictionaryManager.store(app).read())) {
                    RimeConfigHelper.applyCellDictionaries(app)
                    FileLogger.i(TAG, "Online word update applied")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.w(TAG, "Online word update skipped: ${e.message}")
            } finally {
                running.set(false)
            }
        }
    }
}
