package com.kingzcheung.xime.glide

import android.content.Context
import com.kingzcheung.xime.keyboard.LetterKeyRect

/** 滑行词表：随包的 english.txt（按词频排序的常用英文词），与英文补全共用；模板按布局缓存。 */
object GlideLexicon {
    private const val FILE = "english.txt"
    @Volatile private var words: List<String>? = null
    private var cached: GlideDecoder? = null

    fun words(context: Context): List<String> = words ?: synchronized(this) {
        words ?: context.applicationContext.assets.open(FILE).bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }
                .filter { it.length >= 2 && it.all { char -> char in 'a'..'z' } }
                .distinct().toList()
        }.also { words = it }
    }

    /** 布局变化（尺寸、错位、分体）时重建模板；同一布局复用。须在后台线程调用。 */
    fun decoder(context: Context, keyboard: GlideKeyboard): GlideDecoder = synchronized(this) {
        cached?.takeIf { it.revision == keyboard.revision } ?: GlideDecoder(keyboard, words(context)).also { cached = it }
    }

    fun keyboard(keys: Map<Char, LetterKeyRect>): GlideKeyboard {
        val widths = keys.values.map { it.width }.sorted()
        return GlideKeyboard(widths[widths.size / 2],
            keys.mapValues { (_, rect) -> (rect.x + rect.width / 2f) to (rect.y + rect.height / 2f) })
    }
}
