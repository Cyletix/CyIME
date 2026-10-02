package com.kingzcheung.xime.model

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilities
import com.kingzcheung.xime.runtime.adaptation.DeviceKeyEffectDefaults
import com.kingzcheung.xime.settings.KeyEffectPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*

class DeviceModelDefaultsTest {
    @get:Rule val files = TemporaryFolder()
    private val gib = DeviceModelProfiles.GIB
    private fun device(ram: Long? = 8 * gib, cores: Int? = 8, frequency: Long? = 2_400_000,
        lowRam: Boolean? = false) = DeviceCapabilities(0, 35, listOf("arm64-v8a"), lowRam, ram, cores, frequency)

    private class Stores {
        val context = mock<Context>()
        val data = mutableMapOf<String, MutableMap<String, Any>>()
        private val preferences = mutableMapOf<String, SharedPreferences>()
        fun values(name: String = "kime_settings") = data.getOrPut(name) { mutableMapOf() }
        init {
            whenever(context.getSharedPreferences(any(), any())).thenAnswer { call ->
                val name = call.getArgument<String>(0)
                preferences.getOrPut(name) {
                    val values = values(name)
                    mock<SharedPreferences>().also { prefs ->
                        whenever(prefs.all).thenAnswer { values.toMap() }
                        whenever(prefs.contains(any())).thenAnswer { values.containsKey(it.getArgument<String>(0)) }
                        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
                        whenever(prefs.getBoolean(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1) }
                        whenever(prefs.getInt(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Int>(1) }
                        whenever(prefs.getLong(any(), any())).thenAnswer { values[it.getArgument<String>(0)] ?: it.getArgument<Long>(1) }
                        whenever(prefs.edit()).thenAnswer {
                            val pending = mutableMapOf<String, Any?>()
                            val editor = mock<SharedPreferences.Editor>()
                            fun change(call: org.mockito.invocation.InvocationOnMock): SharedPreferences.Editor {
                                pending[call.getArgument(0)] = call.getArgument(1)
                                return editor
                            }
                            whenever(editor.putString(any(), anyOrNull())).thenAnswer(::change)
                            whenever(editor.putBoolean(any(), any())).thenAnswer(::change)
                            whenever(editor.putInt(any(), any())).thenAnswer(::change)
                            whenever(editor.putLong(any(), any())).thenAnswer(::change)
                            whenever(editor.remove(any())).thenAnswer { pending[it.getArgument(0)] = null; editor }
                            fun save() { pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value } }
                            whenever(editor.commit()).thenAnswer { save(); true }
                            doAnswer { save(); null }.whenever(editor).apply()
                            editor
                        }
                    }
                }
            }
        }
    }

    @Test fun memoryAndFrequencyBoundariesSelectConcreteModels() {
        val cases = listOf(
            device(4 * gib - 1) to DeviceModelTier.LIGHT,
            device(4 * gib, frequency = 1_800_000) to DeviceModelTier.STANDARD,
            device(6 * gib - 1) to DeviceModelTier.STANDARD,
            device(6 * gib, cores = 4, frequency = 2_200_000) to DeviceModelTier.ENHANCED,
            device(frequency = 2_199_999) to DeviceModelTier.STANDARD,
            device(frequency = 1_799_999) to DeviceModelTier.LIGHT,
            device(cores = 3) to DeviceModelTier.LIGHT,
            device(lowRam = true) to DeviceModelTier.LIGHT,
        )
        cases.forEach { (hardware, tier) ->
            val profile = DeviceModelProfiles.choose(hardware)
            assertEquals(hardware.toString(), tier, profile.tier)
            assertEquals(if (tier == DeviceModelTier.LIGHT) DeviceModelProfiles.SMALL else DeviceModelProfiles.BASE, profile.predictionModel)
            assertEquals(if (tier == DeviceModelTier.ENHANCED) SpeechModelCatalog.ZIPFORMER_TWO_PASS else SpeechModelCatalog.ZIPFORMER, profile.voiceModel)
        }
    }

    @Test fun missingFrequencyFallsBackToMemoryAndCoresButMissingEssentialsStayConservative() {
        assertEquals(DeviceModelTier.ENHANCED, DeviceModelProfiles.choose(device(frequency = null)).tier)
        assertEquals(DeviceModelTier.STANDARD, DeviceModelProfiles.choose(device(4 * gib, frequency = null)).tier)
        listOf(device(ram = null), device(ram = 0), device(cores = null), device(cores = 0), device(lowRam = null))
            .forEach { assertEquals(DeviceModelTier.LIGHT, DeviceModelProfiles.choose(it).tier) }
    }

    @Test fun freshDevicesUseTheRecommendationInActualSettingsAndDownloadPlan() {
        listOf(device(3 * gib), device(4 * gib), device()).forEach { hardware ->
            val stores = Stores()
            val context = stores.context
            val profile = DeviceModelProfiles.choose(hardware)
            DeviceDefaults.initialize(context) { hardware }
            val effects = DeviceKeyEffectDefaults.choose(hardware)
            assertEquals(effects.glow, stores.values()[KeyEffectPreferences.GLOW])
            assertEquals(effects.animation, stores.values()[KeyEffectPreferences.ANIMATION])
            assertEquals(profile.predictionModel, SettingsPreferences.getPredictionSelectedModel(context))
            assertEquals(profile.voiceModel, AsrModelManager(context).getSelectedModelId())
            assertEquals(profile.tier.name, stores.values()[DeviceDefaults.TIER])
            assertEquals(hardware.maxCpuKHz, stores.values()["device_initial_max_cpu_khz"])
            assertFalse(stores.values().containsKey("prediction_selected_model"))
            assertFalse(stores.values("asr_model").containsKey("selected_model"))
            val expected = setOf("ochwpro", profile.predictionModel, SpeechModelCatalog.ZIPFORMER) +
                if (profile.tier == DeviceModelTier.ENHANCED) setOf(SpeechModelCatalog.SENSEVOICE) else emptySet()
            assertEquals(expected, DefaultModelInstaller.requestedModelIds(context).toSet())
        }
    }

    @Test fun laterLaunchDoesNotProbeAgainOrOverrideManualChoices() {
        val stores = Stores()
        val context = stores.context
        DeviceDefaults.initialize(context) { device() }
        SettingsPreferences.setPredictionSelectedModel(context, DeviceModelProfiles.SMALL)
        AsrModelManager(context).setModel(SpeechModelCatalog.SENSEVOICE)
        DeviceDefaults.initialize(context) { error("must not re-probe") }
        assertEquals(DeviceModelProfiles.SMALL, SettingsPreferences.getPredictionSelectedModel(context))
        assertEquals(SpeechModelCatalog.SENSEVOICE, AsrModelManager(context).getSelectedModelId())
        assertFalse(DefaultModelInstaller.isPendingDefault(context, DeviceModelProfiles.BASE))
        assertFalse(DefaultModelInstaller.isPendingDefault(context, SpeechModelCatalog.ZIPFORMER))
    }

    @Test fun existingEffectSettingsAreNotReclassifiedByHardware() {
        for (initialized in listOf(false, true)) for (glow in listOf(false, true)) {
            val stores = Stores()
            stores.values()[KeyEffectPreferences.GLOW] = glow
            if (initialized) stores.values()[DeviceDefaults.VERSION] = 1
            DeviceDefaults.initialize(stores.context) { error("old installation must not re-probe") }
            val prefs = SettingsPreferences.getPrefsPublic(stores.context)
            assertEquals(glow, KeyEffectPreferences.glowEnabled(prefs))
            assertEquals(glow, KeyEffectPreferences.animationEnabled(prefs))
            KeyEffectPreferences.setAnimationEnabled(prefs, !glow)
            DeviceDefaults.initialize(stores.context) { error("manual selection must be preserved") }
            assertEquals(glow, KeyEffectPreferences.glowEnabled(prefs))
            assertEquals(!glow, KeyEffectPreferences.animationEnabled(prefs))
        }
    }

    @Test fun legacySettingsIncludingValuesEqualToOldDefaultsArePreserved() {
        listOf("kime_settings", "asr_model", "default_models", "bundled_models").forEach { existingStore ->
            val stores = Stores()
            stores.values(existingStore)["legacy_marker"] = true
            DeviceDefaults.initialize(stores.context) { error("existing installation must not re-probe") }
            assertEquals("PRESERVED", stores.values()[DeviceDefaults.TIER])
            assertEquals(DeviceModelProfiles.BASE, SettingsPreferences.getPredictionSelectedModel(stores.context))
            assertEquals(SpeechModelCatalog.ZIPFORMER, AsrModelManager(stores.context).getSelectedModelId())
        }
        val stores = Stores()
        SettingsPreferences.setPredictionSelectedModel(stores.context, DeviceModelProfiles.SMALL)
        AsrModelManager(stores.context).setModel(SpeechModelCatalog.ZIPFORMER_TWO_PASS)
        SettingsPreferences.setSmartPredictionEnabled(stores.context, false)
        DeviceDefaults.initialize(stores.context) { error("existing choices") }
        assertEquals(DeviceModelProfiles.SMALL, SettingsPreferences.getPredictionSelectedModel(stores.context))
        assertEquals(SpeechModelCatalog.ZIPFORMER_TWO_PASS, AsrModelManager(stores.context).getSelectedModelId())
        assertFalse(SettingsPreferences.isSmartPredictionEnabled(stores.context))
    }

    @Test fun disablingCorrectionRemovesOnlyTheCorrectionResourceFromThePendingPlan() {
        val stores = Stores()
        DeviceDefaults.initialize(stores.context) { device() }
        assertTrue(DefaultModelInstaller.isPendingDefault(stores.context, SpeechModelCatalog.SENSEVOICE))
        AsrModelManager(stores.context).setRefinementEnabled(false)
        assertFalse(DefaultModelInstaller.isPendingDefault(stores.context, SpeechModelCatalog.SENSEVOICE))
        assertTrue(DefaultModelInstaller.isPendingDefault(stores.context, SpeechModelCatalog.ZIPFORMER))
    }

    @Test fun disabledOrOnlineFeaturesDoNotAutoDownloadTheirModels() {
        val stores = Stores()
        val context = stores.context
        DeviceDefaults.initialize(context) { device() }
        SettingsPreferences.setSmartPredictionEnabled(context, false)
        SettingsPreferences.setSttEnabled(context, false)
        assertEquals(listOf("ochwpro"), DefaultModelInstaller.requestedModelIds(context))
        SettingsPreferences.setSttEnabled(context, true)
        SettingsPreferences.setSttOnlinePluginId(context, "online-provider")
        assertEquals(listOf("ochwpro"), DefaultModelInstaller.requestedModelIds(context))
        SettingsPreferences.setSttOnlinePluginId(context, "")
        SettingsPreferences.setSttUseLocal(context, false)
        assertEquals(listOf("ochwpro"), DefaultModelInstaller.requestedModelIds(context))
    }

    @Test fun completedOrDeletedModelsAreNotAutomaticallyRestored() {
        val stores = Stores()
        DeviceDefaults.initialize(stores.context) { device(3 * gib) }
        val id = DeviceModelProfiles.SMALL
        assertTrue(DefaultModelInstaller.isPendingDefault(stores.context, id))
        DefaultModelInstaller.markHandled(stores.context, id)
        assertFalse(DefaultModelInstaller.isPendingDefault(stores.context, id))
        DeviceDefaults.initialize(stores.context) { error("already initialized") }
        assertFalse(DefaultModelInstaller.isPendingDefault(stores.context, id))
    }

    @Test fun changingSelectionDuringDownloadPreventsFinalInstallation() {
        val stores = Stores()
        val context = stores.context
        DeviceDefaults.initialize(context) { device(3 * gib) }
        val guard = ModelInstallGuard()
        val generation = guard.currentGeneration()
        val staging = files.newFolder("staging")
        val destination = files.newFolder("installed")
        java.io.File(destination, "existing").writeText("preserve")
        assertTrue(DefaultModelInstaller.isPendingDefault(context, DeviceModelProfiles.SMALL))
        SettingsPreferences.setPredictionSelectedModel(context, DeviceModelProfiles.BASE)
        assertFalse(guard.install(generation, staging, destination,
            { DefaultModelInstaller.isPendingDefault(context, DeviceModelProfiles.SMALL) }) { fail("must not install") })
        assertEquals("preserve", java.io.File(destination, "existing").readText())
        assertTrue(staging.exists())
    }
}
