package com.kingzcheung.xime.speech

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Word n-gram lookups the second-pass gate needs; ids are the model's, -1 for a word it never saw. */
internal interface WordLanguageModel {
    fun id(text: String): Int
    /** ln P of a word without history. */
    fun unigram(id: Int): Double
    /** ln P(word | history). */
    fun bigram(history: Int, word: Int): Double
    /** ln P(word | history2 history). */
    fun trigram(history2: Int, history: Int, word: Int): Double
}

/**
 * Read-only view of the 九键词组模型 word trigram (rime/t9_ngram.lm, format in
 * app/src/main/jni/librime-t9/src/t9_ngram_format.h, versions 2 and compact 3), memory-mapped:
 * the file stays on disk and only the pages a lookup touches are read. Same lookups as
 * tools/ime_lab/ngram/ngram_query.py. Absolute reads only, so threads may share one instance.
 */
internal class WordNgram private constructor(private val map: ByteBuffer) : WordLanguageModel {
    private val compact: Boolean
    private val capacity: Long
    private val unseenLower: Double
    private val unseenMl: Double
    private val shift: Int
    private val hashOffset: Int
    private val wordOffset: Int
    private val beginOffset: Int
    private val nextOffset: Int
    private val logpOffset: Int
    private val backoffOffset: Int
    private val triBeginOffset: Int // version 2
    private val triBaseOffset: Int  // version 3
    private val triLoOffset: Int    // version 3
    private val triNextOffset: Int
    private val triLogpOffset: Int
    val words: Int

    init {
        val magic = ByteArray(8).also { for (i in 0 until 8) it[i] = map.get(i) }.toString(Charsets.US_ASCII)
        compact = magic == "CYT9NGR3"
        require(compact || magic == "CYT9NGR2") { "不是九键词组模型" }
        require(map.getInt(8) == if (compact) 3 else 2) { "九键词组模型版本不对" }
        words = map.getInt(12)
        capacity = map.getLong(40)
        require(capacity > 0 && capacity and (capacity - 1) == 0L) { "九键词组模型损坏" }
        unseenLower = map.getFloat(48).toDouble()
        unseenMl = map.getFloat(52).toDouble()
        fun off(at: Int) = map.getLong(at).also { require(it in 0..map.capacity()) { "九键词组模型损坏" } }.toInt()
        if (compact) {
            shift = map.getInt(56)
            hashOffset = off(64); wordOffset = off(72); beginOffset = off(80); nextOffset = off(88)
            logpOffset = off(96); backoffOffset = off(104); triBaseOffset = off(112); triLoOffset = off(120)
            triNextOffset = off(128); triLogpOffset = off(136); triBeginOffset = 0
        } else {
            shift = 0
            hashOffset = off(56); wordOffset = off(64); beginOffset = off(72); nextOffset = off(80)
            logpOffset = off(88); backoffOffset = off(96); triBeginOffset = off(104); triNextOffset = off(112)
            triLogpOffset = off(120); triBaseOffset = 0; triLoOffset = 0
        }
    }

    /** Model id of a word, or -1 when the model never saw it. */
    override fun id(text: String): Int {
        var h = -0x340d631b7bdddcdbL // 14695981039346656037
        for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 1099511628211L
        if (h == 0L) h = 1
        val mask = capacity - 1
        var i = h and mask
        while (true) {
            if (compact) {
                val slot = map.getLong(hashOffset + (8 * i).toInt())
                if (slot == 0L) return -1
                if ((slot ushr 24) == (h ushr 24)) return ((slot and 0xFFFFFF) - 1).toInt()
            } else {
                val at = hashOffset + (16 * i).toInt()
                val key = map.getLong(at)
                if (key == h) return map.getInt(at + 8)
                if (key == 0L) return -1
            }
            i = (i + 1) and mask
        }
    }

    private fun value(offset: Int, index: Int): Double =
        if (compact) map.getShort(offset + 2 * index) / LOG_SCALE else map.getFloat(offset + 4 * index).toDouble()

    private fun entry(id: Int, field: Int): Double =
        if (compact) map.getShort(wordOffset + 6 * id + 2 * field) / LOG_SCALE
        else map.getFloat(wordOffset + 16 * id + 4 * field).toDouble()

    private fun lower(id: Int) = if (id < 0) unseenLower else entry(id, 0)
    /** ln P of a word without history (corpus frequency). */
    override fun unigram(id: Int) = if (id < 0) unseenMl else entry(id, 1)

    private fun idAt(offset: Int, index: Int): Int = if (compact) {
        val at = offset + 3 * index
        (map.get(at).toInt() and 0xff) or ((map.get(at + 1).toInt() and 0xff) shl 8) or ((map.get(at + 2).toInt() and 0xff) shl 16)
    } else map.getInt(offset + 4 * index)

    private fun find(offset: Int, begin: Int, end: Int, value: Int): Int {
        var lo = begin; var hi = end - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = idAt(offset, mid)
            when {
                v < value -> lo = mid + 1
                v > value -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun bigramIndex(history: Int, word: Int): Int {
        val begin = map.getInt(beginOffset + 4 * history); val end = map.getInt(beginOffset + 4 * history + 4)
        return find(nextOffset, begin, end, word)
    }

    /** ln P(word | history); -1 ids are unseen words. */
    override fun bigram(history: Int, word: Int): Double {
        if (history < 0) return lower(word)
        if (word >= 0) bigramIndex(history, word).let { if (it >= 0) return value(logpOffset, it) }
        return entry(history, 2) + lower(word)
    }

    private fun triBegin(b: Int): Int = if (compact)
        map.getInt(triBaseOffset + 4 * (b ushr shift)) + (map.getShort(triLoOffset + 2 * b).toInt() and 0xffff)
    else map.getInt(triBeginOffset + 4 * b)

    /** ln P(word | history2 history). */
    override fun trigram(history2: Int, history: Int, word: Int): Double {
        val lowerOrder = bigram(history, word)
        if (history2 < 0 || history < 0) return lowerOrder
        val b = bigramIndex(history2, history)
        if (b < 0) return lowerOrder
        if (word >= 0) {
            val k = find(triNextOffset, triBegin(b), triBegin(b + 1), word)
            if (k >= 0) return value(triLogpOffset, k)
        }
        return value(backoffOffset, b) + lowerOrder
    }

    companion object {
        private const val LOG_SCALE = 1024.0

        fun open(file: File): WordNgram = RandomAccessFile(file, "r").use { raf ->
            require(raf.length() in 144 until Int.MAX_VALUE) { "九键词组模型大小不对" }
            WordNgram(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN))
        }
    }
}
