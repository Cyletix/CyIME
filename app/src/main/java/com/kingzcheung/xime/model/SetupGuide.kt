package com.kingzcheung.xime.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import com.kingzcheung.xime.runtime.adaptation.DeviceCapabilityProbe
import com.kingzcheung.xime.settings.InputLanguage
import com.kingzcheung.xime.settings.InputProfiles
import com.kingzcheung.xime.settings.LanguagePreferences
import com.kingzcheung.xime.settings.LayoutKind
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.AsrModelManager
import com.kingzcheung.xime.speech.SpeechModelSelection
import com.kingzcheung.xime.util.FileLogger
import java.util.Locale

/** 「按设备推荐配置」：读手机情况、照方案改设置、删掉用不到的模型。只在用户点了“按推荐设置”后改东西。 */
object SetupGuide {
    private const val KEY_SHOWN = "setup_guide_shown"

    /** 首次设置向导之后是否已经出现过（看过、设置过或点了“以后再说”）。 */
    fun isShown(context: Context): Boolean = SettingsPreferences.getPrefsPublic(context).getBoolean(KEY_SHOWN, false)
    fun markShown(context: Context) { SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_SHOWN, true).apply() }

    /** 在 IO 线程调用：会查模型文件。 */
    fun readFacts(context: Context): SetupFacts {
        val hardware = DeviceCapabilityProbe.read(context)
        ModelManager.initialize()
        val models = SetupGuidePlanner.managedIds.mapNotNull { ModelManager.getModel(it) }
        val installed = models.mapNotNull { model ->
            if (!ModelManager.isModelDownloaded(context, model)) null
            else model.id to ModelManager.getModelSizeOnDisk(context, model.id).takeIf { it > 0 }.let { it ?: catalogBytes(model) }
        }.toMap()
        val inPackage = models.filter { model ->
            model.resolvedVersion()?.let { BundledModelInstaller.packagedSource(context, model, it) } != null
        }.map { it.id }.toSet()
        val connectivity = runCatching { context.getSystemService(ConnectivityManager::class.java) }.getOrNull()
        val network = runCatching { connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) } }.getOrNull()
        val manager = AsrModelManager(context)
        val chinese = runCatching { manager.getSelectedModelId(InputLanguage.CHINESE) }.getOrNull()
        val t9Layout = runCatching {
            InputProfiles.current(SettingsPreferences.getCurrentSchema(context)).layout.kind == LayoutKind.T9
        }.getOrDefault(false)
        // 自己下载过九键词组模型（不是安装包带的那份）也算在用九键。
        val t9Download = listOf(CandidateModelCatalog.NGRAM, CandidateModelCatalog.NGRAM_SMALL).any { id ->
            ModelManager.getModel(id)?.versions?.any { hasDownloadedModelFiles(ModelStorage.getModelDir(context, id), it) } == true
        }
        return SetupFacts(
            tier = DeviceModelProfiles.choose(hardware).tier,
            memoryBytes = hardware.totalMemoryBytes,
            cores = hardware.processors,
            maxCpuKHz = hardware.maxCpuKHz,
            soc = socName(),
            language = Locale.getDefault().let { it.getDisplayLanguage(it) }.takeIf { it.isNotBlank() },
            freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrNull(),
            online = network != null,
            metered = network != null && !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            japanese = InputLanguage.JAPANESE in LanguagePreferences.enabled(context) || Locale.getDefault().language == "ja",
            sttEnabled = SettingsPreferences.isSttEnabled(context),
            streamingVoice = chinese != null && SpeechModelSelection.hasCorrection(chinese),
            usesT9 = t9Layout || t9Download,
            usesCaption = com.kingzcheung.xime.BuildConfig.ELDER ||
                runCatching { com.kingzcheung.xime.ui.caption.CaptionLauncherIcon.isEnabled(context) }.getOrDefault(false),
            predictionEnabled = SettingsPreferences.isSmartPredictionEnabled(context),
            installed = installed,
            inPackage = inPackage,
            sizes = models.associate { it.id to catalogBytes(it) },
        )
    }

    private fun socName(): String? {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
        else ""
        return soc.ifBlank { Build.HARDWARE.takeIf { it.isNotBlank() && it != Build.UNKNOWN } }
    }

    /** 目录里的大小；归档下载的模型文件清单没有字节数，用版本上写的大小（如“136.4MB”）。 */
    internal fun catalogBytes(model: ModelInfo): Long {
        val files = model.files.sumOf { it.sizeBytes }
        if (files > 0) return files
        val size = model.resolvedVersion()?.size.orEmpty()
        val number = Regex("([0-9.]+)\\s*([KMG]B)", RegexOption.IGNORE_CASE).find(size) ?: return 0
        val unit = when (number.groupValues[2].uppercase()) { "KB" -> 1024L; "MB" -> 1024L * 1024; else -> 1024L * 1024 * 1024 }
        return (number.groupValues[1].toDouble() * unit).toLong()
    }

    data class Result(val removed: Int, val freedBytes: Long, val downloading: Long, val waitingForWifi: Boolean, val failed: List<String>)

    /**
     * 照 [plan] 设置：改语音、联想选项，删掉 [removeIds]（安装包里的不再解压），
     * 安装包里有的直接取回，其余排队下载（移动数据下除非 [allowMetered] 否则等 Wi-Fi）。
     */
    suspend fun apply(context: Context, plan: SetupPlan, removeIds: Set<String>, allowMetered: Boolean): Result {
        val app = context.applicationContext
        val facts = readFacts(app)
        val manager = AsrModelManager(app)
        SettingsPreferences.setSttEnabled(app, plan.sttEnabled)
        plan.voiceMode?.let { mode ->
            listOf(InputLanguage.CHINESE, InputLanguage.ENGLISH).forEach { runCatching { manager.setModel(mode, it) } }
        }
        plan.predictionModel?.let { model ->
            if (SettingsPreferences.getPredictionSelectedModel(app) != model) {
                SettingsPreferences.setPredictionSelectedModel(app, model)
                com.kingzcheung.xime.association.AssociationManager.release()
            }
        }
        val keepIds = plan.keep.map { it.id }.toSet()
        // 不在方案里的首次默认模型不再自动下载。
        SetupGuidePlanner.managedIds.filter { it !in keepIds }.forEach { DefaultModelInstaller.markHandled(app, it) }

        val removing = plan.remove.filter { it.id in removeIds }
        BundledModelInstaller.skip(app, removing.map { it.id }.filter { it in BundledModelInstaller.modelIds }.toSet())
        val failed = mutableListOf<String>()
        var freed = 0L
        for (item in removing) {
            // 安装包带的九键词组模型还没放进词库目录：关掉它，启动时就不会再放进去。
            if (item.source != SetupSource.ON_PHONE) {
                if (ModelManager.getModel(item.id)?.category == ModelCategory.CANDIDATE) CandidateModelAssets.setDisabled(app, item.id, true)
                continue
            }
            if (ModelManager.deleteModel(app, item.id)) freed += item.bytes
            else failed += item.title
        }

        val missing = plan.keep.filter { it.id !in facts.installed }
        val fromPackage = missing.filter { it.id in facts.inPackage }
        val fromNetwork = missing.filterNot { it.id in facts.inPackage }
        for (item in fromPackage) ModelManager.getModel(item.id)?.let { ModelManager.downloadModelInBackground(app, it) }
        var waiting = false
        if (fromNetwork.isNotEmpty()) {
            DefaultModelInstaller.requestGuided(app, fromNetwork.map { it.id }.toSet(), allowMetered)
            waiting = facts.metered && !allowMetered
            // 已在跑的首次下载可能还没看到新加的这几项：能下就直接开始，同一模型的下载会合并。
            if (facts.online && !waiting) fromNetwork.forEach { item ->
                ModelManager.getModel(item.id)?.let { ModelManager.downloadModelInBackground(app, it, onlyIfDefaultPending = true) }
            }
        }
        markShown(app)
        FileLogger.i("SetupGuide", "按推荐设置：语音=${plan.voiceMode} 联想=${plan.predictionModel} 九键=${plan.t9Model} " +
            "删除=${removing.map { it.id }} 取回=${fromPackage.map { it.id }} 下载=${fromNetwork.map { it.id }}")
        return Result(removing.count { it.source == SetupSource.ON_PHONE } - failed.size, freed, fromNetwork.sumOf { it.bytes }, waiting, failed)
    }
}
