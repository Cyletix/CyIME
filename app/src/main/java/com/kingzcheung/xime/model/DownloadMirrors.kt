package com.kingzcheung.xime.model

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import java.util.TimeZone

/** 模型下载走哪条线路：自动判断，或用户手动固定。 */
enum class DownloadRegion(val label: String, val description: String) {
    AUTO("自动", "按 SIM 卡、网络运营商和时区判断是否在中国大陆"),
    CHINA("中国大陆优先", "先用魔搭、HuggingFace 国内镜像等国内线路，失败再用原站"),
    GLOBAL("海外优先", "先用 GitHub、HuggingFace 等原站，失败再用国内线路"),
}

/**
 * 同一个文件的多条下载线路。所有线路都必须提供完全相同的文件：
 * 下载后按目录里的 sha256 校验，不一致就换下一条，不会装进错误的文件。
 */
object DownloadMirrors {
    private const val PREFS = "model_download_source"
    private const val KEY_REGION = "region"

    /** 国内可直连的主机。 */
    private val domesticHosts = setOf("hf-mirror.com", "www.modelscope.cn", "modelscope.cn")

    /** 已知内容相同的固定替换：原站前缀 → 国内镜像前缀。 */
    private val prefixMirrors = listOf(
        "https://huggingface.co/" to "https://hf-mirror.com/",
    )

    private val chinaTimeZones = setOf("Asia/Shanghai", "Asia/Chongqing", "Asia/Chungking",
        "Asia/Harbin", "Asia/Urumqi", "Asia/Kashgar", "PRC")

    fun region(context: Context): DownloadRegion =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_REGION, null)
            ?.let { saved -> DownloadRegion.entries.firstOrNull { it.name == saved } } ?: DownloadRegion.AUTO

    fun setRegion(context: Context, region: DownloadRegion) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_REGION, region.name).apply()
    }

    fun prefersDomestic(context: Context): Boolean = when (region(context)) {
        DownloadRegion.CHINA -> true
        DownloadRegion.GLOBAL -> false
        DownloadRegion.AUTO -> detectChina(context)
    }

    /** 不联网判断：SIM 或当前网络国家码为 cn，没有 SIM 时看时区。 */
    internal fun detectChina(context: Context): Boolean {
        val telephony = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull()
        val countries = listOfNotNull(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
        ).map { it.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }
        if (countries.isNotEmpty()) return "cn" in countries
        return TimeZone.getDefault().id in chinaTimeZones
    }

    /** 按线路偏好排好序、去重后的下载地址；[primary] 为目录里登记的原地址。 */
    fun candidates(primary: String, extra: List<String>, preferDomestic: Boolean): List<String> {
        val all = (listOf(primary) + extra).filter { it.isNotBlank() }
        val expanded = all.flatMap { url ->
            listOf(url) + prefixMirrors.mapNotNull { (from, to) ->
                if (url.startsWith(from)) to + url.removePrefix(from) else null
            }
        }.distinct()
        val (domestic, global) = expanded.partition { isDomestic(it) }
        return if (preferDomestic) domestic + global else global + domestic
    }

    fun candidates(context: Context, primary: String, extra: List<String> = emptyList()): List<String> =
        candidates(primary, extra, prefersDomestic(context))

    internal fun isDomestic(url: String): Boolean =
        url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase(Locale.ROOT) in domesticHosts
}
