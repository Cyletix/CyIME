package com.kingzcheung.xime.model

import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuidePlanTest {
    private val mb = 1024L * 1024
    private val gb = 1024 * mb
    private val sizes = mapOf(
        SpeechModelCatalog.SENSEVOICE to 228 * mb, SpeechModelCatalog.PARAFORMER to 226 * mb, SpeechModelCatalog.XASR to 136 * mb,
        SpeechModelCatalog.ZIPFORMER to 133 * mb, SpeechModelCatalog.ZIPFORMER_JA_INT8 to 153 * mb, SpeechModelCatalog.ZIPFORMER_JA to 586 * mb,
        BuiltinModelCatalog.SPEAKER_MODEL to 27 * mb, DeviceModelProfiles.BASE to 35 * mb, DeviceModelProfiles.SMALL to 19 * mb,
        CandidateModelCatalog.NGRAM to 203 * mb, CandidateModelCatalog.NGRAM_SMALL to 105 * mb, CandidateModelCatalog.GRAMMAR to 399 * mb,
        "ochwpro" to 7 * mb,
    )
    private fun facts(tier: DeviceModelTier, free: Long? = 50 * gb, installed: Set<String> = emptySet(),
        inPackage: Set<String> = emptySet(), metered: Boolean = false, japanese: Boolean = false) = SetupFacts(
        tier = tier, memoryBytes = when (tier) { DeviceModelTier.LIGHT -> 3 * gb; DeviceModelTier.STANDARD -> 4 * gb; else -> 8 * gb },
        freeBytes = free, metered = metered, japanese = japanese,
        installed = installed.associateWith { sizes.getValue(it) }, inPackage = inPackage, sizes = sizes)
    private fun SetupPlan.keepIds() = keep.map { it.id }.toSet()
    private fun SetupPlan.removeIds() = remove.map { it.id }.toSet()

    @Test fun lightPhoneOnLiteGetsTheSmallestWorkingSet() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.LIGHT), SetupAnswers(VoiceUse.SOMETIMES, t9 = false, caption = false))
        assertEquals(setOf(SpeechModelCatalog.SENSEVOICE, DeviceModelProfiles.SMALL, "ochwpro"), plan.keepIds())
        assertEquals(SpeechModelCatalog.SENSEVOICE, plan.voiceMode)
        assertTrue(plan.keep.all { it.source == SetupSource.DOWNLOAD })
        assertTrue(plan.remove.isEmpty())
    }

    @Test fun fullEditionDropsWhatThePersonDoesNotUse() {
        // 瘦身完整版：SenseVoice、两个联想、声纹、手写解压在手机上，九键词组模型在词库目录里。
        val bundled = setOf(SpeechModelCatalog.SENSEVOICE, DeviceModelProfiles.BASE, DeviceModelProfiles.SMALL,
            BuiltinModelCatalog.SPEAKER_MODEL, "ochwpro", CandidateModelCatalog.NGRAM)
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.ENHANCED, installed = bundled, inPackage = bundled),
            SetupAnswers(VoiceUse.SOMETIMES, t9 = false, caption = false))
        assertEquals(setOf(SpeechModelCatalog.SENSEVOICE, DeviceModelProfiles.BASE, "ochwpro"), plan.keepIds())
        assertEquals(setOf(DeviceModelProfiles.SMALL, BuiltinModelCatalog.SPEAKER_MODEL, CandidateModelCatalog.NGRAM), plan.removeIds())
        assertEquals((19 + 27 + 203) * mb, plan.freedBytes)
        assertEquals(0L, plan.downloadBytes)
        assertTrue(plan.notes.any { "安装包" in it })
    }

    @Test fun frequentVoiceOnStrongPhoneStreamsAndRefines() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.ENHANCED), SetupAnswers(VoiceUse.OFTEN, t9 = true, caption = false))
        assertEquals(SpeechModelCatalog.TWO_PASS, plan.voiceMode)
        assertTrue(plan.keepIds().containsAll(setOf(SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.SENSEVOICE,
            SpeechModelCatalog.XASR, CandidateModelCatalog.NGRAM)))
    }

    @Test fun frequentVoiceOnLightPhoneStaysOnOneModelAndSaysWhy() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.LIGHT), SetupAnswers(VoiceUse.OFTEN, t9 = true, caption = false))
        assertEquals(SpeechModelCatalog.SENSEVOICE, plan.voiceMode)
        assertFalse(SpeechModelCatalog.PARAFORMER in plan.keepIds())
        assertEquals(CandidateModelCatalog.NGRAM_SMALL, plan.t9Model)
        assertTrue(plan.notes.any { "内存 3 GB" in it })
    }

    @Test fun lowSpacePicksSmallerModels() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.STANDARD, free = 1200 * mb),
            SetupAnswers(VoiceUse.OFTEN, t9 = true, caption = false))
        assertEquals(SpeechModelCatalog.SENSEVOICE, plan.voiceMode)
        assertEquals(CandidateModelCatalog.NGRAM_SMALL, plan.t9Model)
        assertEquals(DeviceModelProfiles.SMALL, plan.predictionModel)
        assertTrue(plan.notes.any { "1.2 GB" in it })
    }

    @Test fun noVoiceTurnsVoiceOffAndRemovesEveryVoiceModel() {
        val installed = setOf(SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.XASR, "ochwpro")
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.ENHANCED, installed = installed),
            SetupAnswers(VoiceUse.NEVER, t9 = false, caption = false))
        assertFalse(plan.sttEnabled)
        assertNull(plan.voiceMode)
        assertEquals(setOf(SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.PARAFORMER, SpeechModelCatalog.XASR), plan.removeIds())
    }

    @Test fun captionKeepsVoiceModelsEvenWithoutVoiceTyping() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.STANDARD), SetupAnswers(VoiceUse.NEVER, t9 = false, caption = true))
        assertFalse(plan.sttEnabled)
        assertTrue(plan.keepIds().containsAll(setOf(SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.PARAFORMER,
            BuiltinModelCatalog.SPEAKER_MODEL)))
        assertFalse(SpeechModelCatalog.XASR in plan.keepIds())
    }

    @Test fun meteredNetworkWaitsForWifi() {
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.STANDARD, metered = true),
            SetupAnswers(VoiceUse.SOMETIMES, t9 = false, caption = false))
        assertTrue(plan.downloadBytes > 0)
        assertTrue(plan.notes.any { "Wi-Fi" in it })
    }

    @Test fun japaneseKeepsItsChosenModelOnlyWhenJapaneseIsUsed() {
        val installed = setOf(SpeechModelCatalog.SENSEVOICE, SpeechModelCatalog.ZIPFORMER_JA_INT8)
        val answers = SetupAnswers(VoiceUse.SOMETIMES, t9 = false, caption = false)
        assertTrue(SpeechModelCatalog.ZIPFORMER_JA_INT8 in
            SetupGuidePlanner.plan(facts(DeviceModelTier.STANDARD, installed = installed, japanese = true), answers).keepIds())
        assertTrue(SpeechModelCatalog.ZIPFORMER_JA_INT8 in
            SetupGuidePlanner.plan(facts(DeviceModelTier.STANDARD, installed = installed), answers).removeIds())
    }

    @Test fun keepsTheNgramTierAlreadyInstalledAndDropsOldGrammar() {
        val installed = setOf(CandidateModelCatalog.NGRAM_SMALL, CandidateModelCatalog.GRAMMAR)
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.ENHANCED, installed = installed),
            SetupAnswers(VoiceUse.SOMETIMES, t9 = true, caption = false))
        assertEquals(CandidateModelCatalog.NGRAM_SMALL, plan.t9Model)
        assertEquals(setOf(CandidateModelCatalog.GRAMMAR), plan.removeIds())
    }

    @Test fun suggestionFollowsCurrentUse() {
        val f = facts(DeviceModelTier.ENHANCED).copy(streamingVoice = true, usesT9 = true)
        assertEquals(SetupAnswers(VoiceUse.OFTEN, t9 = true, caption = false), SetupGuidePlanner.suggest(f))
        assertEquals(VoiceUse.NEVER, SetupGuidePlanner.suggest(f.copy(sttEnabled = false)).voice)
        assertEquals(VoiceUse.SOMETIMES, SetupGuidePlanner.suggest(f.copy(tier = DeviceModelTier.LIGHT)).voice)
    }

    @Test fun everyItemIsExplainedInPlainWords() {
        val all = SetupGuidePlanner.managedIds.toSet()
        val plan = SetupGuidePlanner.plan(facts(DeviceModelTier.ENHANCED, installed = all), SetupAnswers(VoiceUse.OFTEN, true, true))
        (plan.keep + plan.remove).forEach { item ->
            assertTrue(item.id, item.reason.isNotBlank())
            assertFalse(item.title, Regex("Paraformer|Zipformer|SenseVoice|X-ASR").containsMatchIn(item.title + item.reason))
        }
    }
}
