package com.kingzcheung.xime.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DownloadMirrorsTest {
    private val hf = "https://huggingface.co/csukuangfj/m/resolve/abc/tokens.txt"
    private val github = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/a.tar.bz2"
    private val modelscope = "https://www.modelscope.cn/models/bikeand/asr/resolve/master/a.tar.bz2"

    @Test fun chinaPrefersDomesticMirrorsThenOriginals() {
        assertEquals(listOf("https://hf-mirror.com/csukuangfj/m/resolve/abc/tokens.txt", hf),
            DownloadMirrors.candidates(hf, emptyList(), preferDomestic = true))
        assertEquals(listOf(modelscope, github), DownloadMirrors.candidates(github, listOf(modelscope), preferDomestic = true))
    }

    @Test fun overseasKeepsOriginalFirstButStillFallsBackToDomestic() {
        assertEquals(listOf(hf, "https://hf-mirror.com/csukuangfj/m/resolve/abc/tokens.txt"),
            DownloadMirrors.candidates(hf, emptyList(), preferDomestic = false))
        assertEquals(listOf(github, modelscope), DownloadMirrors.candidates(github, listOf(modelscope), preferDomestic = false))
    }

    @Test fun blankAndDuplicateAddressesAreDropped() {
        assertEquals(listOf(github), DownloadMirrors.candidates(github, listOf("", github), preferDomestic = true))
        assertTrue(DownloadMirrors.candidates("", emptyList(), preferDomestic = true).isEmpty())
    }

    /** GitHub 在国内常下不动：商店里每个还能下载的文件都要有一条国内线路（魔搭或 HuggingFace 国内镜像）。 */
    @Test fun everyDownloadableModelHasADomesticRoute() {
        val missing = BuiltinModelCatalog.models.flatMap { model ->
            model.versions.filterNot { it.retired }.flatMap { version ->
                val archive = version.archiveUrl
                if (archive != null) {
                    listOf(model.id to DownloadMirrors.candidates(archive, version.archiveMirrors, preferDomestic = true))
                } else version.files.map { file ->
                    "${model.id}/${file.name}" to DownloadMirrors.candidates(file.downloadUrl, file.mirrors, preferDomestic = true)
                }
            }
        }.filterNot { (_, urls) -> urls.firstOrNull()?.let(DownloadMirrors::isDomestic) == true }.map { it.first }
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun localImportMatchesOnlyCompleteVerifiedVersions() {
        val a = File("a"); val b = File("b"); val archive = File("c")
        val files = ModelInfo("files", "文件模型", "", ModelCategory.PREDICTION, versions = listOf(ModelVersion("v1",
            files = listOf(ModelFile("x.onnx", "u", "AA"), ModelFile("vocab.json", "u", "bb")))))
        val partial = ModelInfo("partial", "缺文件", "", ModelCategory.PREDICTION, versions = listOf(ModelVersion("v1",
            files = listOf(ModelFile("y.onnx", "u", "aa"), ModelFile("z.json", "u", "zz")))))
        val unverified = ModelInfo("unverified", "无哈希", "", ModelCategory.OTHER, versions = listOf(ModelVersion("v1",
            files = listOf(ModelFile("x.onnx", "u", "")))))
        val packed = ModelInfo("packed", "归档模型", "", ModelCategory.ASR, versions = listOf(ModelVersion("v1",
            archiveUrl = "u", sha256 = "CC", files = listOf(ModelFile("tokens.txt", "")))))

        val matches = ModelImporter.matchModels(listOf(files, partial, unverified, packed),
            mapOf("aa" to a, "bb" to b, "cc" to archive))

        assertEquals(listOf("files", "packed"), matches.map { it.model.id })
        assertEquals(mapOf("x.onnx" to a, "vocab.json" to b), matches[0].files)
        assertEquals(archive, matches[1].archive)
    }
}
