package com.kingzcheung.xime.speech

import com.kingzcheung.xime.model.*

/** Pinned official exports, independently available even if the original market is offline. */
object SpeechModelCatalog {
    const val ZIPFORMER = "zipformer-zh-int8"
    const val ZIPFORMER_TWO_PASS = "zipformer-sensevoice"
    const val PARAFORMER = "paraformer-zh-en-int8"
    const val SENSEVOICE = "sensevoice-multilingual-int8"
    const val TWO_PASS = "paraformer-sensevoice"
    val models = listOf(
        ModelInfo("paraformer-zh-en-int8", "Paraformer 中英流式", "说话时持续出字；与 ASRInput 使用同一套中英模型。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "8e40c43232a1", size = "226.2 MB", files = listOf(
                ModelFile("decoder.int8.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/8e40c43232a1c5c66c82111efc5820d3accca11b/decoder.int8.onnx", "f3cca9f77bb9d93c8fcbfb63ae617b6b1ee96818df3aa3b151c40658fe38594f", 71664561L),
                ModelFile("encoder.int8.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/8e40c43232a1c5c66c82111efc5820d3accca11b/encoder.int8.onnx", "81a70226a8934e6ed92aa1d4fc486b428b5398e2f2619ed4897b7294cab90e9a", 165462184L),
                ModelFile("tokens.txt", "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/8e40c43232a1c5c66c82111efc5820d3accca11b/tokens.txt", "59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6", 75756L)
            )))),
        ModelInfo("sensevoice-multilingual-int8", "SenseVoice 多语", "支持中英日粤韩，为 Zipformer 或 Paraformer 提供第二遍校正。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "2365baeacb50", size = "228.5 MB", files = listOf(
                ModelFile("model.int8.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/2365baeacb507f821a0c8120fcee3d484dba7a07/model.int8.onnx", "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51", 239233841L),
                ModelFile("tokens.txt", "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/2365baeacb507f821a0c8120fcee3d484dba7a07/tokens.txt", "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc", 315894L)
            )))),
    )
}
