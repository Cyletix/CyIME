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
