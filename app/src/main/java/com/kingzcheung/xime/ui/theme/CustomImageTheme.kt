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
 */
object CustomImageTheme {
    const val ID = "custom_image"
    private const val NAME = "我的图片"
    private const val KEY_SRC = "custom_theme_image_src"
    private const val KEY_BASE = "custom_theme_image_base"
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

    private fun imageSource(context: Context): String? {
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

    internal fun build(context: Context, themes: List<KeyboardColorScheme>): KeyboardColorScheme? {
        val src = imageSource(context) ?: return null
        val baseId = SettingsPreferences.getPrefsPublic(context).getString(KEY_BASE, null)
        val base = themes.firstOrNull { it.id == baseId && usableBase(it) }
            ?: themes.firstOrNull { it.id == SoftBlueTheme.ID } ?: return null
        baseThemeId = base.id
        fun Color.rgb() = (toArgb() and 0xFFFFFF).toLong()
        return base.copy(
            id = ID, name = NAME,
            keyboardBackground = BackgroundConfig(type = "image", src = src, fit = "cover",
                overlayAlpha = OVERLAY_ALPHA, overlayAlphaDark = OVERLAY_ALPHA,
                overlayColor = base.keyboardBgLight.rgb(), overlayColorDark = base.keyboardBgDark.rgb()),
            candidateBarBackground = null,
        )
    }

    /**
     * 读入 [uri]（按 EXIF 摆正、长边缩到 2048），存成新的 JPEG 并删掉旧图，记下沿用的主题。
     * 在 IO 线程调用；读不出图片时抛异常，原来的图片和设置不变。
     */
    fun save(context: Context, uri: Uri, baseThemeId: String) {
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
        check(SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_SRC, "themes/$name").putString(KEY_BASE, baseThemeId).commit()) { "无法保存设置" }
        dir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) && it.name != name }?.forEach { it.delete() }
    }
}
