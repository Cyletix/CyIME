package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.model.DownloadMirrors

/**
 * 扩展商店（方案、插件）的下载线路，规则和模型下载一样：国内先走魔搭，海外先走 GitHub，失败换下一条。
 *
 * - 商店目录：仓库 `store/` 原样同步到魔搭仓库 Cyletix/cyime-model 的 `store/`（scripts/store/sync_modelscope.py）。
 * - 第三方文件（GitHub 上的方案包）：同步脚本校验 sha256 后转存到魔搭 `store/mirror/<主机>/<路径>`。
 *   只有带 sha256 的文件才换线路，下载后照常校验，不一致就换下一条，不会装进别人改过的文件。
 */
internal object StoreMirrors {
    const val MODELSCOPE_STORE = DownloadMirrors.CYIME_MODELSCOPE + "store/"
    private const val MODELSCOPE_MIRROR = MODELSCOPE_STORE + "mirror/"

    /** 本仓库 store/ 在各条线路上的前缀（与 CYIME_STORE_BASE_URLS 对应）。 */
    private val ownStorePrefixes = listOf(
        "https://raw.githubusercontent.com/Cyletix/CyIME/main/store/",
        "https://cdn.jsdelivr.net/gh/Cyletix/CyIME@main/store/",
        "https://fastly.jsdelivr.net/gh/Cyletix/CyIME@main/store/",
        MODELSCOPE_STORE,
    )

    /** 商店目录的基址按地区排序：国内魔搭在前，海外魔搭在最后。 */
    fun orderedBases(bases: List<String>, preferDomestic: Boolean): List<String> {
        val all = bases.filter { it.isNotBlank() }.map { it.trimEnd('/') + "/" }.distinct()
        val (domestic, global) = all.partition { DownloadMirrors.isDomestic(it) }
        return if (preferDomestic) domestic + global else global + domestic
    }

    fun orderedBases(context: Context, bases: List<String>): List<String> =
        orderedBases(bases, DownloadMirrors.prefersDomestic(context))

    /**
     * 一个商店文件的全部下载地址（已按地区排序）。没有 sha256 时只用原地址：换线路必须能校验内容。
     */
    fun fileCandidates(url: String, sha256: String?, storeBases: List<String>, preferDomestic: Boolean): List<String> {
        if (url.isBlank()) return emptyList()
        if (sha256.isNullOrBlank() || !sha256.trim().matches(Regex("[a-fA-F0-9]{64}"))) return listOf(url)
        val extra = mutableListOf<String>()
        val own = ownStorePrefixes.firstOrNull { url.startsWith(it) }
        if (own != null) {
            val relative = url.removePrefix(own)
            (storeBases + MODELSCOPE_STORE).forEach { extra += it.trimEnd('/') + "/" + relative }
        } else {
            mirrorPath(url)?.let { extra += MODELSCOPE_MIRROR + it }
            jsdelivrOf(url)?.let { extra += it }
        }
        return DownloadMirrors.candidates(url, extra, preferDomestic)
    }

    fun fileCandidates(context: Context, url: String, sha256: String?): List<String> =
        fileCandidates(url, sha256, KeysConfigHelper.loadXimeIndexConfig(context).baseUrls,
            DownloadMirrors.prefersDomestic(context))

    /** 第三方文件在魔搭里的转存路径：主机名 + 原路径（同步脚本用同一条规则）。 */
    internal fun mirrorPath(url: String): String? {
        val rest = url.substringAfter("https://", "").takeIf { it.isNotBlank() } ?: return null
        val host = rest.substringBefore('/')
        if (host !in setOf("raw.githubusercontent.com", "github.com")) return null
        val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
        return path.takeIf { it.isNotBlank() && ".." !in it }?.let { "$host/$it" }
    }

    /** raw.githubusercontent.com/<用户>/<仓库>/<提交>/<路径> → jsDelivr 的同一文件。 */
    internal fun jsdelivrOf(url: String): String? {
        val prefix = "https://raw.githubusercontent.com/"
        if (!url.startsWith(prefix)) return null
        val parts = url.removePrefix(prefix).split('/', limit = 4)
        if (parts.size < 4 || parts.any { it.isBlank() }) return null
        return "https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}"
    }
}
