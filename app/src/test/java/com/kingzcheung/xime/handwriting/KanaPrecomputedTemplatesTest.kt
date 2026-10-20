package com.kingzcheung.xime.handwriting

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KanaPrecomputedTemplatesTest {
    private fun assetFile(name: String) = listOf("src/main/assets/handwriting/$name", "app/src/main/assets/handwriting/$name")
        .map(::File).first { it.isFile }

    /** 发布的公式手写模型 v1 编码器（Release formula-handwriting-v1-encoder.onnx）的哈希。 */
    private val releaseEncoderSha = "c2c55f14e8d3bc892c7f86ec9b237e54f46997300550a5c76501c54b7344b6dd"

    @Test fun shippedVectorsMatchTheReleasedModelAndDecodeToUnitVectors() {
        val meta = assetFile("kana_default_vectors.json").readText(Charsets.UTF_8)
        val bin = assetFile("kana_default_vectors.bin").readBytes()
        assertEquals(releaseEncoderSha, JSONObject(meta).getString("model_sha256"))
        val rows = HandwritingTemplates.decodePrecomputed(meta, bin, releaseEncoderSha)
        assertNotNull(rows)
        rows!!
        assertTrue(rows.size >= 170 * 9)
        val strokes = JSONObject(assetFile("kana_default_strokes.json").readText(Charsets.UTF_8)).getJSONObject("chars")
        assertEquals(strokes.keys().asSequence().toSet(), rows.map { it.first }.toSet())
        for ((label, v) in rows.take(50)) {
            val norm = kotlin.math.sqrt(v.fold(0f) { a, x -> a + x * x })
            assertEquals(label, 1f, norm, 0.01f)
        }
    }

    @Test fun aDifferentModelFallsBackToComputingOnTheDevice() {
        val meta = assetFile("kana_default_vectors.json").readText(Charsets.UTF_8)
        val bin = assetFile("kana_default_vectors.bin").readBytes()
        assertNull(HandwritingTemplates.decodePrecomputed(meta, bin, "0".repeat(64)))
        // 文件被截断也回退
        assertNull(HandwritingTemplates.decodePrecomputed(meta, bin.copyOf(bin.size - 2), releaseEncoderSha))
    }

    @Test fun halfPrecisionValuesDecodeExactly() {
        val bin = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x3C00.toShort()).putShort(0xC000.toShort()).putShort(0x3555.toShort()).putShort(0x0001.toShort()).array()
        val meta = JSONObject().put("model_sha256", "ab").put("dim", 4).put("labels", org.json.JSONArray().put("あ")).toString()
        val v = HandwritingTemplates.decodePrecomputed(meta, bin, "AB")!!.single().second
        assertEquals(1f, v[0], 0f); assertEquals(-2f, v[1], 0f)
        assertEquals(0.33325195f, v[2], 1e-7f); assertEquals(5.9604645e-8f, v[3], 1e-12f)
    }
}
