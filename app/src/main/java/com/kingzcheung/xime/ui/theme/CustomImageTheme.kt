package com.kingzcheung.xime.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File

/**
 * 「我的图片」：用户自选一张图片做键盘背景。
 *
 * 配色沿用选图时正在用的主题，图片上压一层该主题的底色（与大漠流金同一做法），
 * 按键、候选字与原主题的对比度相近；深浅色、玻璃都按原主题走。
 * 图片转成 JPEG 存在 rime/themes/，备份恢复一并带上；换图用新文件名，键盘按文件名重新解码。
 * 选图后先进调整页（拖动、缩放、吸附边缘），确认才存；摆放存成与键盘尺寸无关的 [ThemeImagePlacement]。
 */
object CustomImageTheme {
    const val ID = "custom_image"
    private const val NAME = "我的图片"
    private const val KEY_SRC = "custom_theme_image_src"
    private const val KEY_BASE = "custom_theme_image_base"
    private const val KEY_PLACEMENT = "custom_theme_image_placement"
    private const val FILE_PREFIX = "custom-keyboard-bg-"
    /**
     * 遮罩强度，与大漠流金一致。照片纯黑（浅色）或纯白（深色）处压过之后，
     * 按键文字对底仍有 6:1 以上，次要的候选文字约 3.5:1。
     */
    private const val OVERLAY_ALPHA = 0.72f

    /** 当前沿用的主题；桌面图标配色按它走（[ThemeIconColors.forTheme]）。 */
    @Volatile var baseThemeId: String = SoftBlueTheme.ID
        private set

    private fun themesDir(context: Context) = File(SchemaManager.getRimeDir(context), "themes")

    fun hasImage(context: Context): Boolean = imageSource(context) != null

    /** 这张背景是不是用户自己选的图（按存图的文件名认）。 */
    fun isCustomImage(background: BackgroundConfig?): Boolean =
        background?.type == "image" && listOfNotNull(background.src, background.srcDark)
            .any { it.removePrefix("themes/").startsWith(FILE_PREFIX) }

    /** 已存的图片（themes/…），没有时为 null。 */
    fun imageSource(context: Context): String? {
        val src = SettingsPreferences.getPrefsPublic(context).getString(KEY_SRC, null) ?: return null
        return src.takeIf { File(themesDir(context), src.removePrefix("themes/")).isFile }
    }

    /** 透明玻璃、跟随壁纸和自身不能做底：它们没有固定的底色可压在图片上。 */
    private fun usableBase(theme: KeyboardColorScheme) =
        theme.id != ID && theme.id != TransparentGlassTheme.ID && !theme.isDynamic

    /** 选图时要沿用的主题：当前就是「我的图片」时沿用它原来的底。 */
    fun baseFor(context: Context, currentThemeId: String): String =
        if (currentThemeId == ID) SettingsPreferences.getPrefsPublic(context).getString(KEY_BASE, null) ?: SoftBlueTheme.ID
        else currentThemeId

    /** 已存的摆放；旧版本没有这一项，按原来的居中铺满。 */
    internal fun placement(context: Context): ThemeImagePlacement =
        ThemeImagePlacement.decode(SettingsPreferences.getPrefsPublic(context).getString(KEY_PLACEMENT, null))

    internal fun build(context: Context, themes: List<KeyboardColorScheme>): KeyboardColorScheme? {
        val src = imageSource(context) ?: return null
        val baseId = SettingsPreferences.getPrefsPublic(context).getString(KEY_BASE, null)
        val base = resolveBase(themes, baseId) ?: return null
        baseThemeId = base.id
        return scheme(base, src, placement(context))
    }

    /** 调整页预览用：[baseId] 的配色加上还没存的 [src] 与 [placement]。 */
    internal fun preview(themes: List<KeyboardColorScheme>, baseId: String, src: String, placement: ThemeImagePlacement) =
        resolveBase(themes, baseId)?.let { scheme(it, src, placement) }

    /**
     * 调整页预览取键色、字色用的主题 id：预览主题还没存进主题表，按 [ID] 取色会落到全局默认色；
     * 它的键色、字色都抄自沿用的主题，按那个主题取色与存好后的键盘一致。
     */
    internal fun previewColorThemeId(themes: List<KeyboardColorScheme>, baseId: String): String =
        resolveBase(themes, baseId)?.id ?: ID

    private fun resolveBase(themes: List<KeyboardColorScheme>, baseId: String?) =
        themes.firstOrNull { it.id == baseId && usableBase(it) } ?: themes.firstOrNull { it.id == SoftBlueTheme.ID }

    private fun scheme(base: KeyboardColorScheme, src: String, placement: ThemeImagePlacement): KeyboardColorScheme {
        fun Color.rgb() = (toArgb() and 0xFFFFFF).toLong()
        return base.copy(
            id = ID, name = NAME,
            keyboardBackground = BackgroundConfig(type = "image", src = src, fit = placement.fit,
                zoom = placement.zoom, alignX = placement.alignX, alignY = placement.alignY,
                overlayAlpha = OVERLAY_ALPHA, overlayAlphaDark = OVERLAY_ALPHA,
                overlayColor = base.keyboardBgLight.rgb(), overlayColorDark = base.keyboardBgDark.rgb()),
            candidateBarBackground = null,
        )
    }

    /**
     * 读入 [uri]（按 EXIF 摆正、长边缩到 2048），存成新的 JPEG，返回它的 themes/… 路径。
     * 还不启用：调整页确认后 [commit]，取消时 [discard]。
     * 在 IO 线程调用；读不出图片时抛异常，原来的图片和设置不变。
     */
    fun importImage(context: Context, uri: Uri): String {
        val bitmap = com.kingzcheung.xime.ocr.loadOcrGalleryImage(context, uri)
        val dir = themesDir(context).apply { mkdirs() }
        val name = "$FILE_PREFIX${System.currentTimeMillis()}.jpg"
        val target = File(dir, name)
        try {
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "无法保存图片" } }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        return "themes/$name"
    }

    /** 启用 [src] 和它的摆放，记下沿用的主题，删掉其他旧图。在 IO 线程调用。 */
    internal fun commit(context: Context, src: String, baseThemeId: String, placement: ThemeImagePlacement) {
        check(SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_SRC, src).putString(KEY_BASE, baseThemeId)
            .putString(KEY_PLACEMENT, placement.encode()).commit()) { "无法保存设置" }
        val keep = src.removePrefix("themes/")
        themesDir(context).listFiles()?.filter { it.name.startsWith(FILE_PREFIX) && it.name != keep }?.forEach { it.delete() }
    }

    /** 调整页取消：删掉刚导入、没启用的图片；已在用的那张不动。 */
    fun discard(context: Context, src: String) {
        if (src == SettingsPreferences.getPrefsPublic(context).getString(KEY_SRC, null)) return
        File(themesDir(context), src.removePrefix("themes/")).takeIf { it.name.startsWith(FILE_PREFIX) }?.delete()
    }

    /**
     * 删除「我的图片」：清掉设置和图片文件，主题列表里不再有它。
     * 返回原来沿用的主题，正在用「我的图片」时换回它。在 IO 线程调用。
     */
    fun delete(context: Context): String {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val base = prefs.getString(KEY_BASE, null) ?: SoftBlueTheme.ID
        check(prefs.edit().remove(KEY_SRC).remove(KEY_BASE).remove(KEY_PLACEMENT).commit()) { "无法保存设置" }
        themesDir(context).listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
        return base
    }
}
