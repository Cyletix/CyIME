package com.kingzcheung.xime.model

import com.kingzcheung.xime.speech.SpeechModelCatalog

/** 语音输入打算怎么用：向导里的三个选项，说法面向不懂模型名的人。 */
enum class VoiceUse { OFTEN, SOMETIMES, NEVER }

/** 打中文的方式。[KEEP] 只在现在用的是别的方式（仓颉、注音、笔画等）时出现，表示不改。 */
enum class TypingWay { FULL_PINYIN, DOUBLE_PINYIN, WUBI, KEEP }

/**
 * 常见的分不清的读音，一项对应模糊音设置里的一组或几组（[SpellingPatches.Fuzzy] 的 id）。
 * r 和 l 不常见，不在这里问，向导不动它。
 */
enum class FuzzyHabit(val title: String, val example: String, val fuzzyIds: List<String>) {
    RETROFLEX("平翘舌：z 和 zh、c 和 ch、s 和 sh", "zi 也出“知”，si 也出“是”", listOf("z_zh", "c_ch", "s_sh")),
    NL("n 和 l", "nan 也出“蓝”，lan 也出“南”", listOf("n_l")),
    NASAL("前后鼻音：an 和 ang、en 和 eng、in 和 ing", "fan 也出“方”，xin 也出“星”",
        listOf("an_ang", "en_eng", "in_ing", "ian_iang", "uan_uang")),
    FH("f 和 h", "fu 也出“湖”，hu 也出“福”", listOf("f_h")),
}

/** 一种可选的双拼：[example] 是“双拼”两个字的打法，如 ul'pb。 */
data class DoublePinyinChoice(val id: String, val name: String, val example: String, val builtin: Boolean = true)

/** 一台设备在向导眼里的样子；大小都是字节，读不到的为 null。 */
data class SetupFacts(
    val tier: DeviceModelTier,
    /** 给人看的设备叫法：「手机」或「平板」。 */
    val device: String = "手机",
    val memoryBytes: Long? = null,
    val cores: Int? = null,
    val maxCpuKHz: Long? = null,
    val soc: String? = null,
    /** 系统语言的名字，只用来显示。 */
    val language: String? = null,
    /** 应用数据所在分区的剩余空间。 */
    val freeBytes: Long? = null,
    val online: Boolean = true,
    /** 移动数据、热点这类按流量计费的网络。 */
    val metered: Boolean = false,
    /** 启用了日语，或系统语言是日语。 */
    val japanese: Boolean = false,
    val sttEnabled: Boolean = true,
    /** 中文语音当前是否边说边出字（流式预览＋校正）。 */
    val streamingVoice: Boolean = false,
    val usesT9: Boolean = false,
    val usesCaption: Boolean = false,
    val predictionEnabled: Boolean = true,
    /** 已经在手机上的模型：id → 实际占用字节。 */
    val installed: Map<String, Long> = emptyMap(),
    /** 安装包里带着、不联网就能取回的模型 id（完整版）。 */
    val inPackage: Set<String> = emptySet(),
    /** 各模型下载（或从安装包取出）后的大小。 */
    val sizes: Map<String, Long> = emptyMap(),
    /** 现在打中文的方式；九键拼音算全拼，九键、14 键双拼算双拼。 */
    val typing: TypingWay = TypingWay.FULL_PINYIN,
    /** [typing] 是 [TypingWay.KEEP] 时现在那种方式的名字（如“仓颉”）。 */
    val typingName: String = "",
    /** 现在用的双拼（26 键那个方案的 id）；没在用双拼时为 null。 */
    val doublePinyin: String? = null,
    /** 现在是不是中文 26 键（含 QWJRTK 等字母排列）：只有这时才替人切换方案，九键、14 键、手写不动。 */
    val onLetterKeyboard: Boolean = true,
    /** 方案栏里显示着的内置双拼（26 键）。 */
    val shownDoublePinyin: Set<String> = emptySet(),
    /** 能选的双拼：内置的在前，自己建的在后。 */
    val doublePinyinChoices: List<DoublePinyinChoice> = emptyList(),
    /** 五笔86 已经装在手机上。 */
    val wubiInstalled: Boolean = false,
    /** 已打开的模糊音（[SpellingPatches.Fuzzy] 的 id）。 */
    val fuzzy: Set<String> = emptySet(),
)

data class SetupAnswers(
    val voice: VoiceUse,
    val t9: Boolean,
    val caption: Boolean,
    val typing: TypingWay = TypingWay.FULL_PINYIN,
    /** 选双拼时用哪一种。 */
    val doublePinyin: String = FLYPY,
    /** 全拼时分不清的读音（打开模糊音）。 */
    val fuzzy: Set<FuzzyHabit> = emptySet(),
) {
    companion object { const val FLYPY = "double_pinyin_flypy" }
}

/**
 * 打字方式这一块要改什么。全是“和现在不一样才改”：回答没变的项不动，不会把用户细调过的设置冲掉。
 * [lines] 是给人看的每一项改动，[hideLine] 是“方案栏里隐藏用不到的双拼”那一行（页面上单独一个开关）。
 */
data class InputPlan(
    /** 设成当前方案；null 不切。 */
    val switchTo: String? = null,
    /** 要在方案栏打开的双拼。 */
    val showDoublePinyin: String? = null,
    /** 可以从方案栏隐藏的内置双拼。 */
    val hideDoublePinyin: List<String> = emptyList(),
    /** 打字方式选了五笔：没装就下载，没在方案栏就打开。 */
    val useWubi: Boolean = false,
    val downloadWubi: Boolean = false,
    val fuzzyOn: Set<String> = emptySet(),
    val fuzzyOff: Set<String> = emptySet(),
    val lines: List<String> = emptyList(),
    val hideLine: String? = null,
)

enum class SetupSource { ON_PHONE, IN_PACKAGE, DOWNLOAD }

/** 方案里的一项：[title]、[reason] 是给人看的白话，[modelName] 是设置页里同一个模型的名字。 */
data class SetupItem(
    val id: String,
    val title: String,
    val reason: String,
    val bytes: Long,
    val source: SetupSource,
    val modelName: String = "",
)

data class SetupPlan(
    val answers: SetupAnswers,
    val sttEnabled: Boolean,
    /** 中文、英文语音要用的模式；null 表示不改。 */
    val voiceMode: String?,
    /** 智能联想用哪个模型；null 表示不改（联想已关闭）。 */
    val predictionModel: String?,
    /** 九键词组模型用哪一档；null 表示不用。 */
    val t9Model: String?,
    val keep: List<SetupItem>,
    /** 建议删掉的：已经在手机上的，或安装包里带着、还没解压出来的。 */
    val remove: List<SetupItem>,
    val notes: List<String>,
    val input: InputPlan = InputPlan(),
) {
    val downloadBytes: Long get() = keep.filter { it.source == SetupSource.DOWNLOAD }.sumOf { it.bytes }
    val freedBytes: Long get() = remove.filter { it.source == SetupSource.ON_PHONE }.sumOf { it.bytes }
}

/**
 * 按设备和用法推荐要留哪些模型。只用到事实（内存、核心数、主频、剩余空间、网络、语言、
 * 已装的模型），不跑测速；数字依据见各条注释。纯逻辑，便于单元测试。
 */
object SetupGuidePlanner {
    const val GIB = 1024L * 1024 * 1024
    const val HANDWRITING = "ochwpro"

    /** 剩余空间少于这些时改选更省空间的：Android 剩余空间太少时系统本身会变慢、更新也装不上。 */
    const val TIGHT_FREE = 3 * GIB
    const val VERY_TIGHT_FREE = 1 * GIB
    /** 下载后至少还要剩这么多。 */
    const val RESERVE_AFTER_DOWNLOAD = 1536L * 1024 * 1024
    /** 整句精修（X-ASR）再多 136 MB、说完要多跑一个模型：只在性能强、空间宽裕时推荐。 */
    const val XASR_FREE = 6 * GIB

    private val voiceIds = listOf(SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.XASR,
        SpeechModelCatalog.ZIPFORMER, SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA)
    private val predictionIds = listOf(DeviceModelProfiles.BASE, DeviceModelProfiles.SMALL)
    private val t9Ids = listOf(CandidateModelCatalog.NGRAM, CandidateModelCatalog.NGRAM_SMALL, CandidateModelCatalog.GRAMMAR)

    /** 向导会管的全部模型；其他（公式手写、释义词典等）保持原样。 */
    val managedIds: List<String> = voiceIds + BuiltinModelCatalog.SPEAKER_MODEL + predictionIds + t9Ids + HANDWRITING

    /** 白话名字。 */
    fun title(id: String): String = when (id) {
        SpeechModelCatalog.SENSEVOICE -> "语音识别：说完一句出字"
        SpeechModelCatalog.PARAFORMER -> "语音识别：边说边出字"
        SpeechModelCatalog.XASR -> "整句精修（中文）"
        SpeechModelCatalog.ZIPFORMER -> "另一个边说边出字的中文模型"
        SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA -> "日语专用语音模型"
        BuiltinModelCatalog.SPEAKER_MODEL -> "字幕：分清谁在说话"
        DeviceModelProfiles.BASE -> "智能联想（完整）"
        DeviceModelProfiles.SMALL -> "智能联想（轻量）"
        CandidateModelCatalog.NGRAM -> "九键整句组词（完整）"
        CandidateModelCatalog.NGRAM_SMALL -> "九键整句组词（精简）"
        CandidateModelCatalog.GRAMMAR -> "九键语法增强（旧）"
        HANDWRITING -> "手写识别"
        else -> id
    }

    fun suggest(facts: SetupFacts): SetupAnswers = SetupAnswers(
        voice = when {
            !facts.sttEnabled -> VoiceUse.NEVER
            facts.streamingVoice && facts.tier != DeviceModelTier.LIGHT -> VoiceUse.OFTEN
            else -> VoiceUse.SOMETIMES
        },
        t9 = facts.usesT9,
        caption = facts.usesCaption,
        typing = facts.typing,
        doublePinyin = facts.doublePinyin ?: facts.doublePinyinChoices.firstOrNull()?.id ?: SetupAnswers.FLYPY,
        fuzzy = FuzzyHabit.entries.filterTo(linkedSetOf()) { habit -> habit.fuzzyIds.any { it in facts.fuzzy } },
    )

    /** 五笔86 的方案 id（「扩展 › 方案」里审过的那份，约 2 MB）。 */
    const val WUBI = "wubi86"
    const val FULL_PINYIN = "rime_ice"

    fun inputPlan(facts: SetupFacts, answers: SetupAnswers): InputPlan {
        val lines = mutableListOf<String>()
        val typing = answers.typing
        val choice = facts.doublePinyinChoices.firstOrNull { it.id == answers.doublePinyin }
        val target = when (typing) {
            TypingWay.FULL_PINYIN -> FULL_PINYIN
            TypingWay.DOUBLE_PINYIN -> answers.doublePinyin
            TypingWay.WUBI -> WUBI
            TypingWay.KEEP -> null
        }
        val current = when (facts.typing) {
            TypingWay.DOUBLE_PINYIN -> facts.doublePinyin
            TypingWay.FULL_PINYIN -> FULL_PINYIN
            TypingWay.WUBI -> WUBI
            TypingWay.KEEP -> null
        }
        val name = when (typing) {
            TypingWay.FULL_PINYIN -> "全拼"
            TypingWay.DOUBLE_PINYIN -> choice?.name ?: "双拼"
            TypingWay.WUBI -> "五笔86"
            TypingWay.KEEP -> facts.typingName
        }
        val changed = target != null && target != current
        // 已经显示着时再打开一次什么也不改（DoublePinyinSchemes.setEnabled 内容没变不写）。
        val showDoublePinyin = answers.doublePinyin.takeIf { typing == TypingWay.DOUBLE_PINYIN }
        // 已经在用五笔（86、98 或五笔拼音）就不动；从别的方式改成五笔才装、才打开五笔86。
        val useWubi = typing == TypingWay.WUBI && facts.typing != TypingWay.WUBI
        val downloadWubi = useWubi && !facts.wubiInstalled
        if (downloadWubi) lines += "下载五笔86方案（约 2 MB，要联网），装好后在方案栏里打开。"
        val switchTo = target.takeIf { changed && facts.onLetterKeyboard }
        when {
            switchTo != null -> lines += "中文 26 键改用$name。"
            // 全拼（雾凇）一直在方案栏里，不在 26 键上时没有要改的。
            changed && typing != TypingWay.FULL_PINYIN -> lines += "方案栏里打开$name，在键盘上切到中文 26 键就能用；现在的九键、14 键或手写不变。"
            !changed && target != null -> lines += "打字方式还是$name，不变。"
        }
        // 方案栏只留用得到的：内置双拼里除了选中的那种都可以藏起来，自己建的双拼不动。
        val hide = if (typing == TypingWay.KEEP) emptyList()
            else facts.shownDoublePinyin.filter { it != showDoublePinyin }
                .sortedBy { id -> facts.doublePinyinChoices.indexOfFirst { it.id == id } }
        val hideLine = if (hide.isEmpty()) null else {
            val names = hide.map { id -> facts.doublePinyinChoices.firstOrNull { it.id == id }?.name ?: id }
            "方案栏里藏起用不到的 ${hide.size} 种双拼：${names.joinToString("、")}。以后想用，在 设置 → 双拼 里再打开。"
        }

        // 模糊音只对全拼 26 键起作用；只改回答和现在不一样的那几项。
        val fuzzyOn = mutableSetOf<String>()
        val fuzzyOff = mutableSetOf<String>()
        if (typing == TypingWay.FULL_PINYIN) for (habit in FuzzyHabit.entries) {
            val now = habit.fuzzyIds.any { it in facts.fuzzy }
            val wanted = habit in answers.fuzzy
            if (wanted && !now) { fuzzyOn += habit.fuzzyIds; lines += "打开模糊音：${habit.title}。" }
            if (!wanted && now) { fuzzyOff += habit.fuzzyIds.filter { it in facts.fuzzy }; lines += "关掉模糊音：${habit.title}。" }
        }
        return InputPlan(switchTo, showDoublePinyin, hide, useWubi, downloadWubi, fuzzyOn, fuzzyOff,
            lines, hideLine)
    }

    fun plan(facts: SetupFacts, answers: SetupAnswers): SetupPlan {
        val free = facts.freeBytes
        val tight = free != null && free < TIGHT_FREE
        val veryTight = free != null && free < VERY_TIGHT_FREE
        val light = facts.tier == DeviceModelTier.LIGHT
        val notes = mutableListOf<String>()
        fun available(id: String) = id in facts.installed || id in facts.inPackage

        // 语音：说完出字（SenseVoice，错字约为单独边说边出字的一半、带标点）是默认；
        // 边说边出字要再加 226 MB，并且说话时两个模型一起跑，入门机型和空间紧张时不推荐。
        val voiceNeeded = answers.voice != VoiceUse.NEVER || answers.caption
        // 字幕只用说完出字时一整句一下子冒出来，看的人跟不上，所以用字幕也配边说边出字。
        var streaming = voiceNeeded && !light && !tight && (answers.voice == VoiceUse.OFTEN || answers.caption)
        var xasr = streaming && answers.voice == VoiceUse.OFTEN && facts.tier == DeviceModelTier.ENHANCED &&
            (free == null || free >= XASR_FREE)
        if (answers.voice == VoiceUse.OFTEN && !streaming) notes += when {
            light -> "这台${facts.device}${memoryText(facts.memoryBytes)}，边说边出字要同时跑两个语音模型，容易卡，先用「说完一句出字」。"
            else -> "${facts.device}剩余空间不到 3 GB，先用「说完一句出字」，少占 226 MB。"
        }

        // 九键词组：完整 203 MB，精简 105 MB（首选约低 3 个百分点）。已经有哪一档就留哪一档。
        var t9Model = if (!answers.t9) null else when {
            CandidateModelCatalog.NGRAM in facts.installed && !veryTight -> CandidateModelCatalog.NGRAM
            CandidateModelCatalog.NGRAM_SMALL in facts.installed -> CandidateModelCatalog.NGRAM_SMALL
            CandidateModelCatalog.NGRAM in facts.inPackage && !veryTight -> CandidateModelCatalog.NGRAM
            light || tight -> CandidateModelCatalog.NGRAM_SMALL
            else -> CandidateModelCatalog.NGRAM
        }
        // 联想：入门机型或空间很紧用轻量版（18.9 MB），否则完整版（34.8 MB）。
        var prediction = if (!facts.predictionEnabled) null
            else if (light || veryTight) DeviceModelProfiles.SMALL else DeviceModelProfiles.BASE

        fun needed(): List<String> = buildList {
            add(HANDWRITING)
            if (voiceNeeded) add(SpeechModelCatalog.SENSEVOICE)
            if (streaming) add(SpeechModelCatalog.PARAFORMER)
            if (xasr) add(SpeechModelCatalog.XASR)
            if (voiceNeeded && facts.japanese) {
                // 日语已经单独选了模型就留着；SenseVoice 本身也能听日语。
                listOf(SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA)
                    .filter { it in facts.installed }.forEach(::add)
            }
            if (answers.caption) add(BuiltinModelCatalog.SPEAKER_MODEL)
            prediction?.let(::add)
            t9Model?.let(::add)
        }
        fun downloadBytes() = needed().filterNot(::available).sumOf { facts.sizes[it] ?: 0L }

        // 要下载的放不下：按“少了影响最小”的顺序换成更省空间的。
        if (free != null) {
            val steps = listOf(
                { if (xasr) { xasr = false; true } else false },
                { if (streaming && !available(SpeechModelCatalog.PARAFORMER)) { streaming = false; true } else false },
                { if (t9Model == CandidateModelCatalog.NGRAM && !available(CandidateModelCatalog.NGRAM)) {
                    t9Model = CandidateModelCatalog.NGRAM_SMALL; true } else false },
                { if (prediction == DeviceModelProfiles.BASE && !available(DeviceModelProfiles.BASE)) {
                    prediction = DeviceModelProfiles.SMALL; true } else false },
            )
            var downgraded = false
            for (step in steps) {
                if (free - downloadBytes() >= RESERVE_AFTER_DOWNLOAD) break
                if (step()) downgraded = true
            }
            if (downgraded) notes += "${facts.device}剩余空间只有 ${gbText(free)}，选了更省空间的搭配。"
        }

        val keepIds = needed()
        val keep = keepIds.map { id ->
            val source = when {
                id in facts.installed -> SetupSource.ON_PHONE
                id in facts.inPackage -> SetupSource.IN_PACKAGE
                else -> SetupSource.DOWNLOAD
            }
            SetupItem(id, title(id), keepReason(id, facts, answers, streaming), facts.installed[id] ?: facts.sizes[id] ?: 0L,
                source, modelName(id))
        }
        val remove = managedIds.filter { it !in keepIds && available(it) }.map { id ->
            val onPhone = id in facts.installed
            SetupItem(id, title(id), removeReason(id, facts, answers, streaming, prediction, t9Model),
                if (onPhone) facts.installed.getValue(id) else facts.sizes[id] ?: 0L,
                if (onPhone) SetupSource.ON_PHONE else SetupSource.IN_PACKAGE, modelName(id))
        }

        val download = keep.filter { it.source == SetupSource.DOWNLOAD }.sumOf { it.bytes }
        if (download > 0) when {
            !facts.online -> notes += "现在没联网，要下载的 ${mbText(download)} 连上网后自动下载。"
            facts.metered -> notes += "现在用的是移动数据，要下载的 ${mbText(download)} 会等连上 Wi-Fi 再下。"
        }
        if (facts.inPackage.isNotEmpty() && remove.isNotEmpty()) {
            notes += "安装包本身占的空间由系统管理，单独删不掉；这里删的是解压到${facts.device}里的那一份。以后要用，直接从安装包取回，不用联网。"
        }
        val voiceMode = when {
            answers.voice == VoiceUse.NEVER -> null
            streaming -> SpeechModelCatalog.TWO_PASS
            else -> SpeechModelCatalog.SENSEVOICE
        }
        return SetupPlan(answers, answers.voice != VoiceUse.NEVER, voiceMode, prediction, t9Model, keep, remove, notes,
            inputPlan(facts, answers))
    }

    private fun keepReason(id: String, facts: SetupFacts, answers: SetupAnswers, streaming: Boolean): String = when (id) {
        SpeechModelCatalog.SENSEVOICE -> when {
            answers.voice == VoiceUse.NEVER -> "字幕要用它听别人说话。"
            streaming -> "你停下来后，用它把刚才那句再认一遍，改掉错字、加上标点。"
            else -> "说完一句再出字，错字少，自动加标点；中文、英语、日语、粤语都能听。"
        }
        SpeechModelCatalog.PARAFORMER -> if (answers.voice == VoiceUse.OFTEN) "说话时字就跟着出来，不用等说完。"
            else "字幕边听边出字，看的人跟得上。"
        SpeechModelCatalog.XASR -> "说完后把整句中文再认一遍，比上面更准；这台${facts.device}性能够、空间也够。"
        SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA -> "你已经给日语选了它，保留。"
        BuiltinModelCatalog.SPEAKER_MODEL -> "字幕里分清是谁在说话。"
        DeviceModelProfiles.BASE -> "联想下一个词更准，这台${facts.device}跑得动。"
        DeviceModelProfiles.SMALL -> if (facts.tier == DeviceModelTier.LIGHT) "省内存，联想照样能用。" else "省空间，联想照样能用。"
        CandidateModelCatalog.NGRAM -> "九键按整句常用说法组词，长句一次打对的更多。"
        CandidateModelCatalog.NGRAM_SMALL -> "九键按整句组词；比完整版小一半，首选略差一点。"
        HANDWRITING -> "手写输入要用，只有 7 MB。"
        else -> ""
    }

    private fun removeReason(id: String, facts: SetupFacts, answers: SetupAnswers, streaming: Boolean,
        prediction: String?, t9Model: String?): String = when (id) {
        SpeechModelCatalog.SENSEVOICE -> "不用语音，也不用字幕。"
        SpeechModelCatalog.PARAFORMER -> if (answers.voice == VoiceUse.NEVER && !answers.caption) "不用语音，也不用字幕。"
            else "用「说完一句出字」就够了，用不到它。"
        SpeechModelCatalog.XASR -> when {
            answers.voice == VoiceUse.NEVER && !answers.caption -> "不用语音，也不用字幕。"
            streaming -> "整句精修说完要多跑一个模型，这台${facts.device}先不用。"
            else -> "只在边说边出字时才用到。"
        }
        SpeechModelCatalog.ZIPFORMER -> if (answers.voice == VoiceUse.NEVER && !answers.caption) "不用语音，也不用字幕。"
            else "和「边说边出字」作用重复。"
        SpeechModelCatalog.ZIPFORMER_JA_INT8, SpeechModelCatalog.ZIPFORMER_JA -> when {
            answers.voice == VoiceUse.NEVER && !answers.caption -> "不用语音，也不用字幕。"
            facts.japanese -> "日语用「说完一句出字」那个模型就能听。"
            else -> "没有用日语。"
        }
        BuiltinModelCatalog.SPEAKER_MODEL -> "不用字幕。"
        DeviceModelProfiles.BASE, DeviceModelProfiles.SMALL ->
            if (prediction == null) "智能联想已关闭。" else "联想只用一个模型，另一个用不到。"
        CandidateModelCatalog.NGRAM, CandidateModelCatalog.NGRAM_SMALL ->
            if (t9Model == null) "不用九键。" else "九键只用一档，两档二选一。"
        CandidateModelCatalog.GRAMMAR -> if (t9Model == null) "不用九键。" else "有了九键整句组词，它就不再起作用。"
        else -> ""
    }

    /** 设置页、模型管理里同一个模型的名字，方便对照。 */
    private fun modelName(id: String): String = when (id) {
        SpeechModelCatalog.SENSEVOICE -> "SenseVoice"
        SpeechModelCatalog.PARAFORMER -> "Paraformer"
        SpeechModelCatalog.XASR -> "X-ASR"
        SpeechModelCatalog.ZIPFORMER -> "Zipformer · 中文"
        SpeechModelCatalog.ZIPFORMER_JA_INT8 -> "Zipformer · 日语轻量"
        SpeechModelCatalog.ZIPFORMER_JA -> "Zipformer · 日语原版"
        BuiltinModelCatalog.SPEAKER_MODEL -> "声纹模型"
        DeviceModelProfiles.BASE -> "智能联想模型 base 版本"
        DeviceModelProfiles.SMALL -> "智能联想模型 small 版本"
        CandidateModelCatalog.NGRAM -> "九键词组模型（完整）"
        CandidateModelCatalog.NGRAM_SMALL -> "九键词组模型（精简）"
        CandidateModelCatalog.GRAMMAR -> "九键语法增强"
        HANDWRITING -> "手写模型"
        else -> ""
    }

    fun mbText(bytes: Long): String = "${(bytes + 524_288) / 1_048_576} MB"
    fun gbText(bytes: Long): String = "%.1f GB".format(bytes.toDouble() / GIB)
    private fun memoryText(bytes: Long?): String = if (bytes == null) "内存读不到" else "内存 ${(bytes + GIB / 2) / GIB} GB"
}
