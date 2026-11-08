package com.kingzcheung.xime.update

import android.content.Context
import com.kingzcheung.xime.BuildConfig

data class ReleaseNote(val version: String, val markdown: String)

/**
 * 关于 › 更新内容，和更新后设置首页的一次性提示。
 *
 * 说明就是发 Release 用的 docs/<x.y.z>.md，打包时由 prepareReleaseNotes 复制进 assets/release-notes/，
 * 看说明不联网。发布后才改的说明不会进已经打好的安装包。
 */
object ReleaseNotes {
    const val ASSET_DIR = "release-notes"
    private const val PREFS = "release_notes"
    /** 最近一次已经看过（或关掉提示）的版本名。 */
    private const val KEY_SEEN = "seen_version"
    private const val KEY_PROMPT = "prompt_after_update"

    private val fileName = Regex("""^(\d+\.\d+\.\d+)\.md$""")

    fun versionOf(name: String): String? = fileName.matchEntire(name)?.groupValues?.get(1)

    /** 新的在前。 */
    fun sortNewestFirst(notes: List<ReleaseNote>): List<ReleaseNote> =
        notes.sortedWith { a, b -> AppUpdateCatalog.compareVersions(b.version, a.version) }

    /** 打开页面时要展开的版本：上次看过之后、到现在这一版为止的；没有记录时只展开现在这一版。 */
    fun expandedVersions(versions: List<String>, current: String, seen: String?): Set<String> {
        val upToCurrent = versions.filter { AppUpdateCatalog.compareVersions(it, current) <= 0 }
        val newer = if (seen == null) emptyList() else upToCurrent.filter { AppUpdateCatalog.isNewer(it, seen) }
        return (newer.ifEmpty { upToCurrent.filter { AppUpdateCatalog.compareVersions(it, current) == 0 } }).toSet()
    }

    /**
     * 首页要不要出「已更新到 x.y.z」：这一版带了说明、开关开着、这一版还没看过。
     * 新装的人不算更新：第一次进来直接记成看过。
     */
    fun shouldPrompt(seen: String?, current: String, hasCurrentNote: Boolean, enabled: Boolean, freshInstall: Boolean): Boolean =
        enabled && hasCurrentNote && seen != current && !(seen == null && freshInstall)

    fun load(context: Context): List<ReleaseNote> = runCatching {
        val assets = context.assets
        assets.list(ASSET_DIR).orEmpty().mapNotNull { name ->
            val version = versionOf(name) ?: return@mapNotNull null
            val text = runCatching { assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() } }.getOrNull()
            text?.takeIf { it.isNotBlank() }?.let { ReleaseNote(version, it) }
        }.let(::sortNewestFirst)
    }.getOrDefault(emptyList())

    fun hasNote(context: Context, version: String): Boolean =
        runCatching { context.assets.list(ASSET_DIR).orEmpty().contains("$version.md") }.getOrDefault(false)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seenVersion(context: Context): String? = prefs(context).getString(KEY_SEEN, null)

    fun markSeen(context: Context, version: String = BuildConfig.VERSION_NAME) {
        prefs(context).edit().putString(KEY_SEEN, version).apply()
    }

    /** 「更新后提示更新内容」：默认开；自用版天天装包，默认关。 */
    fun isPromptEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_PROMPT, !BuildConfig.DEV_TOOLS)

    fun setPromptEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROMPT, enabled).apply()
    }

    /** 设置首页的一次性提示；老人版没有版本更新这一组，也不出。 */
    fun shouldPromptNow(context: Context): Boolean {
        if (!AppUpdater.supported) return false
        val current = BuildConfig.VERSION_NAME
        val seen = seenVersion(context)
        val fresh = isFreshInstall(context)
        if (seen == null && fresh) { markSeen(context, current); return false }
        return shouldPrompt(seen, current, hasNote(context, current), isPromptEnabled(context), fresh)
    }

    /** 首次安装时间等于最后更新时间：装上以后没覆盖安装过。 */
    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)
}
