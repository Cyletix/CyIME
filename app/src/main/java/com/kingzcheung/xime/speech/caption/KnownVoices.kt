package com.kingzcheung.xime.speech.caption

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * People whose voices the user asked to remember ("记住这个人的声音"): name + voiceprint, so the
 * captions recognise them in every later session without naming them again. Voiceprints stay in
 * app-private storage (files/speaker, excluded from backup and transfer) and never leave the phone.
 * Written only by the :asr process; the caption page reads the names.
 */
internal object KnownVoices {
    data class Voice(val id: Int, val name: String)

    /** Ids of remembered people; automatic speakers count from 1 and the holder is [SpeakerTracker.SELF]. */
    const val FIRST_ID = 1001

    private fun dir(context: Context) = File(context.filesDir, "speaker/known").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "index.tsv")

    @Synchronized fun list(context: Context): List<Voice> = runCatching {
        index(context).takeIf { it.isFile }?.readLines().orEmpty().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab).toIntOrNull()?.let { Voice(it, line.substring(tab + 1)) }
        }
    }.getOrDefault(emptyList())

    fun isKnown(id: Int) = id >= FIRST_ID

    @Synchronized fun nextId(context: Context): Int = (list(context).maxOfOrNull { it.id } ?: (FIRST_ID - 1)) + 1

    @Synchronized fun print(context: Context, id: Int): FloatArray? = readFloats(File(dir(context), "$id.f32"))

    @Synchronized fun save(context: Context, id: Int, name: String, print: FloatArray) {
        writeFloats(File(dir(context), "$id.f32"), print)
        writeIndex(context, list(context).filter { it.id != id } + Voice(id, name.replace('\t', ' ').replace('\n', ' ')))
    }

    @Synchronized fun remove(context: Context, id: Int) {
        File(dir(context), "$id.f32").delete()
        writeIndex(context, list(context).filter { it.id != id })
    }

    private fun writeIndex(context: Context, voices: List<Voice>) =
        replace(index(context), voices.joinToString("") { "${it.id}\t${it.name}\n" }.toByteArray())

    fun readFloats(file: File): FloatArray? = runCatching {
        val bytes = file.takeIf { it.isFile }?.readBytes() ?: return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size / 4) { buffer.float }
    }.getOrNull()

    fun writeFloats(file: File, values: FloatArray) {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        replace(file, buffer.array())
    }

    /** New file first, then rename: a crash never leaves a half-written voiceprint. */
    private fun replace(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
    }
}
