package com.kingzcheung.xime.model

import com.kingzcheung.xime.settings.BundledRimeSync
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CandidateModelInstallationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun version() = ModelVersion("v1", files = listOf(
        ModelFile("t9_sentence.onnx", "", hash("model".toByteArray()), 5),
        ModelFile("t9_sentence.vocab", "", hash("words".toByteArray()), 5),
    ))
    private fun source() = temporary.newFolder().apply {
        File(this, "t9_sentence.onnx").writeText("model")
        File(this, "t9_sentence.vocab").writeText("words")
    }

    @Test fun verifiedDownloadBecomesReadableAtRimePathsAndCanBeReused() {
        val source = source()
        val runtime = temporary.newFolder()
        assertTrue(installCandidateModel(source, runtime, version()))
        assertEquals("model", File(runtime, "t9_sentence.onnx").readText())
        assertEquals("words", File(runtime, "t9_sentence.vocab").readText())
        assertFalse(installCandidateModel(source, runtime, version()))
        assertEquals(setOf("t9_sentence.onnx", "t9_sentence.vocab"), runtime.list()!!.toSet())
    }

    @Test fun corruptedDownloadPublishesNothingAndLeavesUserFilesAlone() {
        val source = source()
        File(source, "t9_sentence.vocab").writeText("wrong")
        val runtime = temporary.newFolder()
        File(runtime, "custom_phrase.txt").writeText("personal")
        assertThrows(IOException::class.java) { installCandidateModel(source, runtime, version()) }
        assertEquals(listOf("custom_phrase.txt"), runtime.list()!!.toList())
        assertEquals("personal", File(runtime, "custom_phrase.txt").readText())
    }

    @Test fun differentExistingModelIsNotOverwrittenOrPartiallyInstalled() {
        val runtime = temporary.newFolder()
        File(runtime, "t9_sentence.onnx").writeText("custom model")
        assertThrows(IOException::class.java) { installCandidateModel(source(), runtime, version()) }
        assertEquals("custom model", File(runtime, "t9_sentence.onnx").readText())
        assertFalse(File(runtime, "t9_sentence.vocab").exists())
    }

    @Test fun lighterBundleDoesNotDeletePreviouslyInstalledEnhancements() {
        val runtime = temporary.newFolder()
        val model = File(runtime, "t9_sentence.onnx").apply { writeText("previous model") }
        val grammar = File(runtime, "wanxiang-lts-zh-hans.gram").apply { writeText("previous grammar") }
        val bytes = "dictionary".toByteArray()
        val manifest = "${hash(bytes)}\t${bytes.size}\trime_chinese/basic.dict.yaml\tbasic.dict.yaml\n"
        BundledRimeSync.install(runtime, temporary.newFolder(), manifest) { bytes.inputStream() }
        assertEquals("previous model", model.readText())
        assertEquals("previous grammar", grammar.readText())
    }

    @Test fun immutableGithubAssetDownloadsRequestBinaryWithoutAffectingOtherHosts() {
        val grammar = CandidateModelCatalog.models.first { it.id == CandidateModelCatalog.GRAMMAR }
        assertEquals("application/octet-stream", modelDownloadRequest(grammar.files.single().downloadUrl).build().header("Accept"))
        assertNull(modelDownloadRequest("https://example.org/repos/a/b/releases/assets/1").build().header("Accept"))
        assertNull(modelDownloadRequest("https://api.github.com/repos/a/b").build().header("Accept"))
        assertTrue(CandidateModelCatalog.models.all { it.category == ModelCategory.CANDIDATE })
    }
}
