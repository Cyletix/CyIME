package com.kingzcheung.xime.model

/** Optional scoring resources; ordinary Chinese dictionaries do not depend on these downloads. */
object CandidateModelCatalog {
    const val GRAMMAR = "t9-grammar"
    const val SENTENCE = "t9-sentence"
    private const val SOURCE = "https://raw.githubusercontent.com/Cyletix/CyIME/6cc85b1c08a90a68eca0bd8d018fdb33e7e3d791/app/src/main/assets/rime_chinese/"
    val models = listOf(
        ModelInfo(GRAMMAR, "九键语法增强", "增强中文九键组句排序，安装后重新启动输入法生效；未下载也可使用基础词库。来源：RIME-LMDG，CC-BY-4.0。", ModelCategory.CANDIDATE,
            versions = listOf(ModelVersion(version = "lts-602588267", size = "390.4 MB", files = listOf(
                ModelFile("wanxiang-lts-zh-hans.gram", "https://api.github.com/repos/amzxyz/RIME-LMDG/releases/assets/602588267",
                    "873cbbb359fcf4df8b200183683ddc8be7b321eac4c864f8d2c7fc3136d4279f", 409412652L),
            )))),
        ModelInfo(SENTENCE, "九键长句增强", "增强中文九键长句候选排序，安装后重新启动输入法生效；老人包已内置。来源：UER-py，Apache-2.0。", ModelCategory.CANDIDATE,
            versions = listOf(ModelVersion(version = "uer-int8-v1", size = "33.8 MB", files = listOf(
                ModelFile("t9_sentence.vocab", SOURCE + "t9_sentence.vocab",
                    "45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c", 109540L),
                ModelFile("t9_sentence.onnx", SOURCE + "t9_sentence.onnx",
                    "159576a96e282f929f5726d53bae5322dc41235d3deef40e806297d9e69ca4fc", 35300003L),
            )))),
    )
}
