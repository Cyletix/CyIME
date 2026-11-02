package com.kingzcheung.xime.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.kingzcheung.xime.stats.TypingStatsData
import com.kingzcheung.xime.stats.TypingStatsText
import java.io.File

/**
 * 打字统计的分享：一张图（发 B站、QQ群）或一段文字。内容只有内置名称和数字，
 * 用户在系统分享面板里看得到、自己决定发不发；App 不联网、不上传。
 */
internal object TypingStatsShare {
    private const val DIR = "stats_share"
    private const val FILE = "typing-speed.png"

    data class CardColors(val background: Int, val onBackground: Int, val muted: Int, val accent: Int)

    fun deviceName(context: Context): String =
        if (context.resources.configuration.smallestScreenWidthDp >= 600) "平板" else "手机"

    fun shareText(context: Context, stats: TypingStatsData, version: String) {
        val text = TypingStatsText.shareText(stats, deviceName(context), version)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "分享打字速度").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 在后台线程调用：画图、写到缓存目录，返回给分享面板用的 Uri。 */
    fun writeCard(context: Context, stats: TypingStatsData, version: String, colors: CardColors): android.net.Uri {
        val bitmap = render(stats, deviceName(context), version, colors)
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, FILE)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareCard(context: Context, uri: android.net.Uri, stats: TypingStatsData, version: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // 有的 App 只收图，有的图文都收：文字和图里写的一样。
            putExtra(Intent.EXTRA_TEXT, TypingStatsText.shareText(stats, deviceName(context), version))
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享打字速度").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 1080 宽的卡片：标题、每种输入方式一行（名字、速度、小字、字数比例条）、页脚。 */
    private fun render(stats: TypingStatsData, device: String, version: String, colors: CardColors): Bitmap {
        val rows = TypingStatsText.shareRows(stats)
        val width = 1080
        val pad = 64f
        val rowHeight = 168f
        val headerHeight = 200f
        val footerHeight = 110f
        val height = (pad * 2 + headerHeight + rowHeight * rows.size.coerceAtLeast(1) + footerHeight).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 玻璃、图片主题的 surface 可能是半透明的；分享出去的图必须是实底，按文字深浅补一个底色。
        val textIsLight = android.graphics.Color.luminance(colors.onBackground) > 0.5f
        canvas.drawColor(if (textIsLight) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt())
        canvas.drawColor(colors.background)

        val title = paint(colors.onBackground, 60f, bold = true)
        val sub = paint(colors.muted, 34f)
        val label = paint(colors.onBackground, 42f, bold = true)
        val detail = paint(colors.muted, 32f)
        val speed = paint(colors.accent, 54f, bold = true)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.muted; alpha = 40 }

        var y = pad + 64f
        canvas.drawText("我的 CyIME 打字速度", pad, y, title)
        y += 64f
        canvas.drawText("$device · 各输入方式累计", pad, y, sub)
        y = pad + headerHeight

        val maxChars = rows.maxOfOrNull { it.chars }?.coerceAtLeast(1) ?: 1
        val right = width - pad
        if (rows.isEmpty()) {
            canvas.drawText("暂无数据", pad, y + 60f, detail)
        }
        for (c in rows) {
            val speedText = TypingStatsText.speedText(c) ?: "—"
            val speedWidth = speed.measureText(speedText)
            canvas.drawText(speedText, right - speedWidth, y + 52f, speed)
            canvas.drawText(ellipsize(c.label, label, right - pad - speedWidth - 32f), pad, y + 50f, label)
            canvas.drawText(ellipsize(TypingStatsText.comboDetail(c), detail, right - pad), pad, y + 100f, detail)
            val barTop = y + 124f
            canvas.drawRoundRect(RectF(pad, barTop, right, barTop + 12f), 6f, 6f, track)
            val w = ((right - pad) * c.chars / maxChars).coerceAtLeast(12f)
            canvas.drawRoundRect(RectF(pad, barTop, pad + w, barTop + 12f), 6f, 6f, bar)
            y += rowHeight
        }
        canvas.drawText("仅含统计数字，不含输入内容 · CyIME $version", pad, height - pad - 10f, sub)
        return bitmap
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
