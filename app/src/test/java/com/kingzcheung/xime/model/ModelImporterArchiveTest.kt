package com.kingzcheung.xime.model

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/** 用户直接选 sherpa-onnx 官方压缩包（Paraformer 等按单个文件登记的模型）时也要能认出来。 */
class ModelImporterArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tokens = "<blk> 0\n你 1\n".toByteArray()
    private val encoder = ByteArray(4096) { (it % 251).toByte() }
    private val fp32 = ByteArray(9000) { 7 }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tar(name: String, wrap: (OutputStream) -> OutputStream, entries: Map<String, ByteArray>): File {
        val file = tmp.newFile(name)
        TarArchiveOutputStream(wrap(file.outputStream())).use { tar ->
            entries.forEach { (path, bytes) ->
                tar.putArchiveEntry(TarArchiveEntry(path).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    private val packEntries get() = mapOf(
        "sherpa-onnx-demo/tokens.txt" to tokens,
        "sherpa-onnx-demo/encoder.int8.onnx" to encoder,
        "sherpa-onnx-demo/encoder.onnx" to fp32,
        "sherpa-onnx-demo/test_wavs/0.wav" to ByteArray(tokens.size) { 1 }, // 大小撞上也要按哈希丢掉
    )

    @Test fun recognisesArchivesByHeaderNotName() {
        assertEquals(ModelImporter.ArchiveKind.TAR_BZ2, ModelImporter.archiveKind(tar("a", ::BZip2CompressorOutputStream, packEntries)))
        assertEquals(ModelImporter.ArchiveKind.TAR_GZ, ModelImporter.archiveKind(tar("b.bin", ::GzipCompressorOutputStream, packEntries)))
        assertEquals(ModelImporter.ArchiveKind.TAR, ModelImporter.archiveKind(tar("c.tar", { it }, packEntries)))
        assertNull(ModelImporter.archiveKind(tmp.newFile("tokens.txt").apply { writeBytes(tokens) }))
        assertNull(ModelImporter.archiveKind(tmp.newFile("empty.onnx")))
    }

    @Test fun keepsOnlyCatalogFilesFromAnArchive() {
        val work = tmp.newFolder("work")
        val shas = setOf(sha(tokens), sha(encoder))
        val sizes = setOf(tokens.size.toLong(), encoder.size.toLong())
        for (archive in listOf(tar("p.tar.bz2", ::BZip2CompressorOutputStream, packEntries), tar("p.tgz", ::GzipCompressorOutputStream, packEntries))) {
            work.listFiles()!!.forEach { it.delete() }
            val kept = ModelImporter.unpackKnownEntries(archive, work, { it in sizes }) { it in shas }
            assertEquals(listOf("encoder.int8.onnx", "tokens.txt"), kept.map { it.name.substringAfter("__") }.sorted())
            assertEquals(kept.toSet(), work.listFiles()!!.toSet()) // fp32、测试音频都没有留在暂存区
            assertEquals(shas, kept.map { sha(it.readBytes()) }.toSet())
        }
    }

    @Test fun unknownArchiveLeavesNothingBehind() {
        val work = tmp.newFolder("work")
        val kept = ModelImporter.unpackKnownEntries(tar("x.tar.gz", ::GzipCompressorOutputStream, packEntries), work, { true }) { false }
        assertTrue(kept.isEmpty())
        assertTrue(work.listFiles()!!.isEmpty())
    }

    @Test fun looseFilesWithUnknownSizeAreNotCopied() {
        val intake = ModelImporter.Intake(tmp.newFolder("work"), setOf(100L, 200L))
        assertTrue(intake.wants("encoder.int8.onnx", 100L))
        assertTrue(!intake.wants("encoder.onnx", 636_348_877L))
        assertTrue(intake.wants("pack.tar.bz2", 1_047_319_737L)) // 压缩包要解开才知道
        assertTrue(intake.wants("model", 5L)) // 没有扩展名，可能是压缩包
        assertTrue(intake.wants("tokens.txt", -1L)) // 大小未知
        assertEquals(listOf("encoder.onnx"), intake.skipped)
        val noFilter = ModelImporter.Intake(tmp.newFolder("w2"), null)
        assertTrue(noFilter.wants("encoder.onnx", 636_348_877L))
    }

    @Test fun sizeFilterTurnsOffWhenAHashedFileHasNoSize() {
        fun model(vararg files: ModelFile) = ModelInfo("m", "m", "", ModelCategory.ASR, versions = listOf(ModelVersion("v1", files = files.toList())))
        val archiveOnly = ModelFile("tokens.txt", "", "", 0L) // 归档模型的清单：没哈希也没大小，不影响
        assertEquals(setOf(10L, 20L), ModelImporter.knownLooseSizes(listOf(model(ModelFile("a", "u", "aa", 10L), ModelFile("b", "u", "bb", 20L), archiveOnly))))
        assertNull(ModelImporter.knownLooseSizes(listOf(model(ModelFile("a", "u", "aa", 10L), ModelFile("b", "u", "bb", 0L)))))
    }
}
