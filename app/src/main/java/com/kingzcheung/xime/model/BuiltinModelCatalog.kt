package com.kingzcheung.xime.model

/** Versioned local catalog: downloads go directly to model publishers, without a market request. */
object BuiltinModelCatalog {
    /** CyIME 自己 Release 里的原样转存（许可见该 Release 的 NOTICE-models.txt），海外线路优先。 */
    private const val RELEASE = "https://github.com/Cyletix/CyIME/releases/download/models/"
    const val SPEAKER_MODEL = "speaker-campplus-zh"
    const val SPEAKER_MODEL_FILE = "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"

    val models = com.kingzcheung.xime.speech.SpeechModelCatalog.models + CandidateModelCatalog.models + listOf(
        ModelInfo("ochwpro", "手写模型", "本地手写识别模型。", ModelCategory.HANDWRITING,
            versions = listOf(ModelVersion(version = "v1.0", size = "6.7 MB",
                files = listOf(
                    ModelFile("ochwpro.onnx", "https://www.modelscope.cn/models/bikeand/ochwpro/resolve/master/ochwpro.onnx", "eb04d62a314c7d7bac4e34d6ce0c24137474d30ff94b929115a621385420ce13", 7001270L),
                    ModelFile("char_index.json", "https://www.modelscope.cn/models/bikeand/ochwpro/resolve/master/char_index.json", "171cf2ac23731428ac9dd28f64be82dece5f66a0ca3d674ffb3b567b51532176", 51317L),
                )))),
        // 公式手写（LaTeX）：tools/handwriting_training 训练，只用公开的 MathWriting 2024，不含用户样本；
        // 日语、英文手写的“边写边学”也用它算特征。
        ModelInfo("handwriting-formula", "公式手写模型", "手写数学公式，识别成 LaTeX；日语、英文手写的边写边学也需要它。", ModelCategory.HANDWRITING,
            versions = listOf(ModelVersion(version = "v1", size = "32.4 MB",
                files = listOf(
                    ModelFile("formula_encoder.onnx", RELEASE + "formula-handwriting-v1-encoder.onnx", "c2c55f14e8d3bc892c7f86ec9b237e54f46997300550a5c76501c54b7344b6dd", 21137800L),
                    ModelFile("formula_decoder.onnx", RELEASE + "formula-handwriting-v1-decoder.onnx", "ab11c1ceb4cb58b044be89b77c71eb32fcbc53ff1ddfc136c51444b002bdb2a9", 13346580L),
                    ModelFile("formula_vocab.json", RELEASE + "formula-handwriting-v1-vocab.json", "73653abb6993d5f2451501c804fb66671c05f86b4165bbe10cb3ef6ef96ec92c", 2003L),
                    ModelFile("formula_meta.json", RELEASE + "formula-handwriting-v1-meta.json", "2ad149afaa9f3872f872e1d4bf9560dfb67be21d08d69ec1a0fa05441cff7541", 205L),
                )))),
        ModelInfo("predictive-text-base", "智能联想模型 base 版本", "较大的本地联想模型。", ModelCategory.PREDICTION,
            versions = listOf(ModelVersion(version = "v1.0", size = "34.8 MB",
                files = listOf(
                    ModelFile("vocab.json", "https://www.modelscope.cn/models/bikeand/predictive-text-base/resolve/master/onnx/vocab.json", "3f7a6aa773afe6dacf75701f7861257d36a46a26ac70c0f8ee6dd4032cc3b9c2", 141139L, mirrors = listOf(RELEASE + "predictive-text-vocab-v1.0.json")),
                    ModelFile("model_int8_dynamic.onnx", "https://www.modelscope.cn/models/bikeand/predictive-text-base/resolve/master/onnx/model_int8_dynamic.onnx", "e15009c84d9702056ba8b5f6c04b27ae7d0400167647a5e94cb699f24f885a9d", 36353598L, mirrors = listOf(RELEASE + "predictive-text-base-v1.0-model_int8_dynamic.onnx")),
                )))),
        ModelInfo("predictive-text-small", "智能联想模型 small 版本", "轻量本地联想模型。", ModelCategory.PREDICTION,
            versions = listOf(ModelVersion(version = "v1.0", size = "18.9 MB",
                files = listOf(
                    ModelFile("vocab.json", "https://www.modelscope.cn/models/bikeand/predictive-text-small/resolve/master/vocab.json", "3f7a6aa773afe6dacf75701f7861257d36a46a26ac70c0f8ee6dd4032cc3b9c2", 141139L, mirrors = listOf(RELEASE + "predictive-text-vocab-v1.0.json")),
                    ModelFile("model_int8_dynamic.onnx", "https://www.modelscope.cn/models/bikeand/predictive-text-small/resolve/master/model_int8_dynamic.onnx", "85b9e8915bd22a5ac1312146df37d77ee3453b9c836c89e666977e7c118a4225", 19720256L, mirrors = listOf(RELEASE + "predictive-text-small-v1.0-model_int8_dynamic.onnx")),
                )))),
        // 候选注释“释义”用的词典（tools/dictionaries/glossary/build_glossary.py 生成）；id 与 CandidateGlossary.MODEL_ID 一致。
        ModelInfo("candidate-gloss", "候选中英释义词典", "设置里“候选注释内容”选释义后，中文候选旁显示英文，中文键盘里的英文候选旁显示中文。英→中取自 ECDICT，中→英取自 CC-CEDICT（MDBG）；常见的日常短语也显示逐词英文。", ModelCategory.OTHER,
            // v5：去掉俚语／粗口义项，多音字按 Rime 常用读音、义项按英文反查来选（v1 有 几号 → heroin）；
            // 补齐常用单字和词组；不可靠的单字释义带 “~”；输入法词库里常见的日常短语预先逐词拼好英文（带 “+”，只显示）。
            versions = listOf(ModelVersion(version = "v5", size = "2.1 MB",
                files = listOf(
                    ModelFile("cyime_gloss-v5.tsv", "https://github.com/Cyletix/CyIME/releases/download/models/cyime_gloss-v5.tsv", "e129bda7454ffeca8b0445c9791be67e027f0874952b5a0ef2ea8d4ccf8fc85b", 2127575L),
                )))),
        // 字幕模式分说话人用的声纹模型（3D-Speaker CAM++ 中文，sherpa-onnx 导出，Apache-2.0）；不是语音识别模型。
        ModelInfo(SPEAKER_MODEL, "声纹模型 · 中文", "字幕模式用来区分不同说话人。", ModelCategory.SPEAKER,
            versions = listOf(ModelVersion(version = "v1.0", size = "27.0 MB",
                files = listOf(
                    // 上游 Release 标签就拼作 recongition；字面量写全，打包脚本按文本解析目录。
                    ModelFile("3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx", "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx", "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11", 28281138L),
                )))),
        ModelInfo("zipformer-zh-int8", "Zipformer · 中文", "中文流式语音识别，可使用 SenseVoice 二次校正。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "v1.0", size = "132.63MB",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2", sha256 = "5a2832047ea1f97dd0dc595b816c230c4bafad65cfc0341fa57517cadc50afd0",
                // 魔搭上的同一归档（2026-10-12 核对哈希一致），国内线路优先用它。
                archiveMirrors = listOf("https://www.modelscope.cn/models/bikeand/asr/resolve/master/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2"),
                files = listOf(
                    ModelFile("encoder.int8.onnx", "", "", 0L),
                    ModelFile("decoder.onnx", "", "", 0L),
                    ModelFile("joiner.int8.onnx", "", "", 0L),
                    ModelFile("tokens.txt", "", "", 0L),
                )))),
    )
}
