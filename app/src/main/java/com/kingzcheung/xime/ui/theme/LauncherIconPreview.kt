package com.kingzcheung.xime.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.kingzcheung.xime.R

internal fun launcherIconResource(style: VisualStyle, framed: Boolean): Int = if (framed) {
    when (style) {
        VisualStyle.NEON -> R.mipmap.cyime_launcher_neon
        VisualStyle.GLASS -> R.mipmap.cyime_launcher_glass
        VisualStyle.FROST -> R.mipmap.cyime_launcher_frost
        else -> R.mipmap.cyime_launcher_facet
    }
} else {
    when (style) {
        VisualStyle.NEON -> R.drawable.cyime_launcher_bare_neon
        VisualStyle.GLASS -> R.drawable.cyime_launcher_bare_glass
        VisualStyle.FROST -> R.drawable.cyime_launcher_bare_frost
        else -> R.drawable.cyime_launcher_bare_facet
    }
}

/** Draw the actual launcher resource, including Android's adaptive mask. */
internal fun renderLauncherIcon(context: Context, style: VisualStyle, framed: Boolean, size: Int = 192): Bitmap {
    val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, launcherIconResource(style, framed), context.theme))
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
    }
}

@Composable
internal fun LauncherIconPreview(style: VisualStyle, framed: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context, style, framed) { renderLauncherIcon(context, style, framed).asImageBitmap() }
    Image(bitmap, "${style.title} · ${if (framed) "带底板" else "无底板"}", modifier)
}

/** 「跟随主题色」时的桌面图标（带底板），[color] 是 [ThemeIconColors.forTheme] 的配色名。 */
internal fun themeLauncherIconResource(color: String): Int = when (color) {
    "softblue" -> R.mipmap.cyime_launcher_theme_softblue
    "sakura" -> R.mipmap.cyime_launcher_theme_sakura
    "sunset" -> R.mipmap.cyime_launcher_theme_sunset
    "aurora" -> R.mipmap.cyime_launcher_theme_aurora
    "mint" -> R.mipmap.cyime_launcher_theme_mint
    "cream" -> R.mipmap.cyime_launcher_theme_cream
    "desert" -> R.mipmap.cyime_launcher_theme_desert
    "mono" -> R.mipmap.cyime_launcher_theme_mono
    else -> R.mipmap.cyime_launcher_theme_lavender
}

/** 设置页预览「跟随主题色」的桌面图标：画的是当前键盘主题对应的那个图标资源。 */
@Composable
internal fun ThemeLauncherIconPreview(themeId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val color = ThemeIconColors.forTheme(themeId)
    val bitmap = remember(context, color) {
        val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, themeLauncherIconResource(color), context.theme))
        Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(Canvas(bitmap))
        }.asImageBitmap()
    }
    Image(bitmap, "跟随主题色", modifier)
}
