package com.kingzcheung.xime.model

/** Versioned local catalog: downloads go directly to model publishers, without a market request. */
object BuiltinModelCatalog {
    /** CyIME 自己 Release 里的原样转存（许可见该 Release 的 NOTICE-models.txt），海外线路优先。 */
    private const val RELEASE = "https://github.com/Cyletix/CyIME/releases/download/models/"
    const val SPEAKER_MODEL = "speaker-campplus-zh"
    const val SPEAKER_MODEL_FILE = "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
    /** 拍照识别（PP-OCRv5 mobile）。轻量版不带，第一次拍照识别时下载；带模型的安装包仍内置在 assets/ocr/ppocr-v5。 */
    const val OCR_MODEL = "ocr-ppocr-v5"

    val models = com.kingzcheung.xime.speech.SpeechModelCatalog.models + CandidateModelCatalog.models + listOf(
        ModelInfo("ochwpro", "手写模型", "本地手写识别模型。", ModelCategory.HANDWRITING,
            // v1.1：在 v1.0 上用用户贡献的手写样本微调（只改 100 个常用字的输出层），字表不变。最新的放第一个。
            versions = listOf(ModelVersion(version = "v1.1", size = "6.7 MB",
                files = listOf(
                    ModelFile("ochwpro.onnx", "https://www.modelscope.cn/models/Cyletix/cyime-model/resolve/master/ochwpro-v1.1-ochwpro.onnx", "d625e6b6fa0dc39499bc15a8ac960249fcea9e71e34334522d32524557d1136d", 7001270L, mirrors = listOf(RELEASE + "ochwpro-v1.1-ochwpro.onnx")),
                    ModelFile("char_index.json", "https://www.modelscope.cn/models/bikeand/ochwpro/resolve/master/char_index.json", "171cf2ac23731428ac9dd28f64be82dece5f66a0ca3d674ffb3b567b51532176", 51317L, mirrors = listOf(RELEASE + "ochwpro-v1.0-char_index.json")),
                )),
                ModelVersion(version = "v1.0", size = "6.7 MB",
                files = listOf(
                    ModelFile("ochwpro.onnx", "https://www.modelscope.cn/models/bikeand/ochwpro/resolve/master/ochwpro.onnx", "eb04d62a314c7d7bac4e34d6ce0c24137474d30ff94b929115a621385420ce13", 7001270L, mirrors = listOf(RELEASE + "ochwpro-v1.0-ochwpro.onnx")),
                    ModelFile("char_index.json", "https://www.modelscope.cn/models/bikeand/ochwpro/resolve/master/char_index.json", "171cf2ac23731428ac9dd28f64be82dece5f66a0ca3d674ffb3b567b51532176", 51317L, mirrors = listOf(RELEASE + "ochwpro-v1.0-char_index.json")),
                )))),
        // 拍照识别：PP-OCRv5 mobile 检测和识别模型（PaddlePaddle，RapidAI/RapidOCR v3.9.2 的 ONNX，Apache-2.0，未改动）；
        // 字符表从识别模型元数据原样取出。国内走魔搭（RapidAI 原仓库、CyIME 仓库），海外走 CyIME Release 转存。
        ModelInfo(OCR_MODEL, "拍照识别模型", "拍照识字用的中英文识别模型，约 21 MB。轻量版第一次拍照识别时下载，完整版已内置。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v5-mobile", size = "20.5 MB",
                files = listOf(
                    ModelFile("ch_PP-OCRv5_det_mobile.onnx", "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/onnx/PP-OCRv5/det/ch_PP-OCRv5_det_mobile.onnx", "4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae", 4819576L,
                        mirrors = listOf(RELEASE + "ocr-ppocr-v5-det-mobile.onnx", DownloadMirrors.CYIME_MODELSCOPE + "ocr-ppocr-v5-det-mobile.onnx")),
                    ModelFile("ch_PP-OCRv5_rec_mobile.onnx", "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/onnx/PP-OCRv5/rec/ch_PP-OCRv5_rec_mobile.onnx", "5825fc7ebf84ae7a412be049820b4d86d77620f204a041697b0494669b1742c5", 16631306L,
                        mirrors = listOf(RELEASE + "ocr-ppocr-v5-rec-mobile.onnx", DownloadMirrors.CYIME_MODELSCOPE + "ocr-ppocr-v5-rec-mobile.onnx")),
                    // 识别模型自带的字符表，RapidAI 同一版本里有原样的一份 (2026-11-03 核对哈希一致)。
                    ModelFile("ppocrv5-characters.txt", "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/paddle/PP-OCRv5/rec/ch_PP-OCRv5_rec_mobile/ppocrv5_dict.txt", "d1979e9f794c464c0d2e0b70a7fe14dd978e9dc644c0e71f14158cdf8342af1b", 74012L,
                        mirrors = listOf(RELEASE + "ocr-ppocr-v5-characters.txt", DownloadMirrors.CYIME_MODELSCOPE + "ocr-ppocr-v5-characters.txt")),
                )))),
        // 公式手写（LaTeX）：tools/handwriting_training 训练，只用公开的 MathWriting 2024，不含用户样本；
        // 日语、英文手写的“边写边学”也用它算特征。
        ModelInfo("handwriting-formula", "公式手写模型", "手写数学公式，识别成 LaTeX；日语、英文手写的边写边学也需要它。", ModelCategory.HANDWRITING,
            versions = listOf(ModelVersion(version = "v1", size = "32.4 MB",
                files = listOf(
                    ModelFile("formula_encoder.onnx", RELEASE + "formula-handwriting-v1-encoder.onnx", "c2c55f14e8d3bc892c7f86ec9b237e54f46997300550a5c76501c54b7344b6dd", 21137800L, mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "formula-handwriting-v1-encoder.onnx")),
                    ModelFile("formula_decoder.onnx", RELEASE + "formula-handwriting-v1-decoder.onnx", "ab11c1ceb4cb58b044be89b77c71eb32fcbc53ff1ddfc136c51444b002bdb2a9", 13346580L, mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "formula-handwriting-v1-decoder.onnx")),
                    ModelFile("formula_vocab.json", RELEASE + "formula-handwriting-v1-vocab.json", "73653abb6993d5f2451501c804fb66671c05f86b4165bbe10cb3ef6ef96ec92c", 2003L, mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "formula-handwriting-v1-vocab.json")),
                    ModelFile("formula_meta.json", RELEASE + "formula-handwriting-v1-meta.json", "2ad149afaa9f3872f872e1d4bf9560dfb67be21d08d69ec1a0fa05441cff7541", 205L, mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "formula-handwriting-v1-meta.json")),
                )))),
        // 候选注释“释义”用的词典（tools/dictionaries/glossary/build_glossary.py 生成）；id 与 CandidateGlossary.MODEL_ID 一致。
        ModelInfo("candidate-gloss", "候选中英释义词典", "设置里“候选注释内容”选释义后，中文候选旁显示英文，中文键盘里的英文候选旁显示中文。英→中取自 ECDICT，中→英取自 CC-CEDICT（MDBG）；常见的日常短语也显示英文。", ModelCategory.OTHER,
            // v8：去掉俚语／粗口义项，多音字按 Rime 常用读音、义项按英文反查来选（v1 有 几号 → heroin）；补齐常用单字和词组；
            // 常见的说话用短语用电脑上离线翻译翻好、且译回中文一致的才收，不收带地名人名的（带 “+”，只显示）。
            // v10：单字只给常用且意思明确的（公开语料最常用约 870 字人工看过），生僻字不显示；公开聊天语料最常用的词组人工补齐、改正错义项；v11 常用实词不再被过严的筛子挡掉，补“X的”形容词；v12 不用截断的长释义，常用动词加“了”给过去式。
            versions = listOf(ModelVersion(version = "v12", size = "2.7 MB",
                files = listOf(
                    ModelFile("cyime_gloss-v12.tsv", "https://github.com/Cyletix/CyIME/releases/download/models/cyime_gloss-v12.tsv", "46ad175c5ea63cf1d9de5999aa615dbaf09a5689e71dc7d20c17e177fd22e761", 2747059L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime_gloss-v12.tsv")),
                )))),
        // 字幕模式分说话人用的声纹模型（3D-Speaker CAM++ 中文，sherpa-onnx 导出，Apache-2.0）；不是语音识别模型。
        ModelInfo(SPEAKER_MODEL, "声纹模型 · 中文", "字幕模式用来区分不同说话人。", ModelCategory.SPEAKER,
            versions = listOf(ModelVersion(version = "v1.0", size = "27.0 MB",
                files = listOf(
                    // 上游 Release 标签就拼作 recongition；字面量写全，打包脚本按文本解析目录。
                    ModelFile("3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx", "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx", "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11", 28281138L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx")),
                )))),
        // 2026-11-03 起不再提供下载：中文边说边出字用 Paraformer，定稿用 SenseVoice / X-ASR（效果更好）。
        // 已经下载、正在用的照旧能用；条目留着，模型管理里可以删。
        ModelInfo("zipformer-zh-int8", "Zipformer · 中文", "中文流式语音识别，可使用 SenseVoice 二次校正。", ModelCategory.ASR,
            versions = listOf(ModelVersion(version = "v1.0", size = "132.63MB", retired = true,
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2", sha256 = "5a2832047ea1f97dd0dc595b816c230c4bafad65cfc0341fa57517cadc50afd0",
                // 魔搭上的同一归档（2026-10-12 核对哈希一致），国内线路优先用它。
                archiveMirrors = listOf("https://www.modelscope.cn/models/bikeand/asr/resolve/master/sherpa-onnx-streaming-zipformer-zh-int8-2025-06-30.tar.bz2"),
                files = listOf(
                    ModelFile("encoder.int8.onnx", "", "", 0L),
                    ModelFile("decoder.onnx", "", "", 0L),
                    ModelFile("joiner.int8.onnx", "", "", 0L),
                    ModelFile("tokens.txt", "", "", 0L),
                )))),
        // 离线翻译（翻译插件没填 API 时用，translate/OfflineTranslation.kt；引擎 libcytranslate.so 在安装包里）。
        // Mozilla Firefox 翻译模型，取自 storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data
        // 的 db/models.json（2026-11-08），解压后原样转存到 Release `models` 和魔搭，文件名前加 translate-<方向>-v1-。
        // 中译英：Firefox「Release Android」版 models/zh-en/cjk_retrain_base-memory_cNY_yaJCStGwnTeXgW8A5w，两边共用一个词表。
        ModelInfo("translate-zh-en", "离线翻译 · 中译英", "翻译插件没填 API 时，在手机上把中文翻成英文，不联网。Mozilla Firefox 的翻译模型。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "52.0 MB",
                files = listOf(
                    ModelFile("model.zhen.intgemm.alphas.bin", RELEASE + "translate-zh-en-v1-model.zhen.intgemm.alphas.bin", "5cd149601802fc8a18124a1c1306144dbbedc058630c4ddb2d53aa76fa9c7c06", 43977787L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-zh-en-v1-model.zhen.intgemm.alphas.bin")),
                    ModelFile("vocab.zhen.spm", RELEASE + "translate-zh-en-v1-vocab.zhen.spm", "dff594318ab7d8b7b60b844ab98ebe6b932ae8045fab15235404c787715965b3", 1359697L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-zh-en-v1-vocab.zhen.spm")),
                    ModelFile("lex.50.50.zhen.s2t.bin", RELEASE + "translate-zh-en-v1-lex.50.50.zhen.s2t.bin", "8524dd4c93ffd7f0ae7be32f77a7c14fea7cdbbebe34ea6ad0ec368d5ddd8b13", 9219192L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-zh-en-v1-lex.50.50.zhen.s2t.bin")),
                )))),
        // 英译中：Firefox「Release」版 models/en-zh/llmaat_finetune10M_qe8_f2_ByQcSxGXQRqGi-UTxYE43g，源、目标各一个词表。
        ModelInfo("translate-en-zh", "离线翻译 · 英译中", "翻译插件没填 API 时，在手机上把英文翻成中文，不联网。Mozilla Firefox 的翻译模型。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "48.7 MB",
                files = listOf(
                    ModelFile("model.enzh.intgemm.alphas.bin", RELEASE + "translate-en-zh-v1-model.enzh.intgemm.alphas.bin", "ce4486f728641a36269a245248dcb53405e76d96d9eba68dcb4172f29521e092", 42992955L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-en-zh-v1-model.enzh.intgemm.alphas.bin")),
                    ModelFile("srcvocab.enzh.spm", RELEASE + "translate-en-zh-v1-srcvocab.enzh.spm", "bd9b65504acc6d9726dd281f7defc2adb7c2c22d0688fe2f84697de25197c8c5", 806952L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-en-zh-v1-srcvocab.enzh.spm")),
                    ModelFile("trgvocab.enzh.spm", RELEASE + "translate-en-zh-v1-trgvocab.enzh.spm", "aded6993c36e440284d11cec3f6b8aef9c0e43188a772d80be342a713adf223d", 772004L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-en-zh-v1-trgvocab.enzh.spm")),
                    ModelFile("lex.50.50.enzh.s2t.bin", RELEASE + "translate-en-zh-v1-lex.50.50.enzh.s2t.bin", "4a5e5827788060f1d718a8132b69440929387514a045796e9b77f935db68c055", 6506248L,
                        mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "translate-en-zh-v1-lex.50.50.enzh.s2t.bin")),
                )))),
        // 字母逐字上屏的语言（LetterLanguage）的补全词表：不进安装包，开启语言时下载。
        // 源文件在 tools/wordlists（build_words.py 生成，来源见 NOTICE），原样传到 Release `models` 和魔搭。
        ModelInfo("words-es", "西班牙语补全词表", "西班牙语键盘的单词补全，约 4 万个常用词。不打重音也能补全：cancion → canción、ano → año。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "0.34 MB",
                files = listOf(ModelFile("cyime-words-es-v1.txt", RELEASE + "cyime-words-es-v1.txt", "883f448de2eb617dd1e8269d8b3ce6c63a253c08c2b4e7ec9df27cfc606be9cf", 351490L,
                    mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime-words-es-v1.txt")))))),
        ModelInfo("words-de", "德语补全词表", "德语键盘的单词补全，约 4 万个常用词。不打变音也能补全：uber → über、strasse → Straße。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "0.35 MB",
                files = listOf(ModelFile("cyime-words-de-v1.txt", RELEASE + "cyime-words-de-v1.txt", "0b72230518bd82fe1e19b28f54154b2fdf91d4513c4c87cdec700ed051b2203a", 365973L,
                    mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime-words-de-v1.txt")))))),
        ModelInfo("words-fr", "法语补全词表", "法语键盘的单词补全，约 4 万个常用词。不打重音也能补全：tres → très、francais → français。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "0.32 MB",
                files = listOf(ModelFile("cyime-words-fr-v1.txt", RELEASE + "cyime-words-fr-v1.txt", "5ed4378b44aecce3afba69f312bbb802f53ede4b8f8c3a941370a9a791b95a19", 339957L,
                    mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime-words-fr-v1.txt")))))),
        ModelInfo("words-it", "意大利语补全词表", "意大利语键盘的单词补全，约 4 万个常用词。不打重音也能补全：perche → perché、citta → città。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "0.34 MB",
                files = listOf(ModelFile("cyime-words-it-v1.txt", RELEASE + "cyime-words-it-v1.txt", "a746aaaea5d3142fe22dc940cf8f390709242bddbc7a71d4dca67124cfc1d5a2", 356137L,
                    mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime-words-it-v1.txt")))))),
        ModelInfo("words-ru", "俄语补全词表", "俄语键盘的单词补全，约 4 万个常用词。不打 ё 也能补全：еще → ещё。", ModelCategory.OTHER,
            versions = listOf(ModelVersion(version = "v1", size = "0.62 MB",
                files = listOf(ModelFile("cyime-words-ru-v1.txt", RELEASE + "cyime-words-ru-v1.txt", "7a818abbe5e85670b76cdac369453848c664540122cf33112365eae749728177", 654612L,
                    mirrors = listOf(DownloadMirrors.CYIME_MODELSCOPE + "cyime-words-ru-v1.txt")))))),
    )
}
