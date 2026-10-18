package com.kingzcheung.xime.service

import android.app.ActivityManager
import android.content.Context
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.model.CandidateModelCatalog
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File

/**
 * 与打字速度有关的本机状态：各用户词库在磁盘上的体积、可选的九键语法/长句增强与智能联想、内存余量。
 * 只记库名、字节数、开关与条目总数，不读取任何词条内容。
 */
internal object InputInventory {
    fun describe(context: Context): String {
        val rime = File(context.filesDir, "rime")
        val stores = rime.listFiles().orEmpty()
            .filter { it.name.endsWith(".userdb") || it.name.contains("userdict", ignoreCase = true) || it.name.endsWith(".userdb.txt") }
            .sortedBy { it.name }
            .joinToString(",") { "${it.name}:${sizeOf(it) / 1024}KB" }
        val predictionModel = SettingsPreferences.getPredictionSelectedModel(context)
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }
        return "userdata=[$stores] " +
            "grammar=${installed(rime, CandidateModelCatalog.GRAMMAR)} " +
            "sentence=${installed(rime, CandidateModelCatalog.SENTENCE)} " +
            "prediction=${SettingsPreferences.isSmartPredictionEnabled(context)}/$predictionModel/" +
            "${ModelStorage.getModelDir(context, predictionModel).listFiles().orEmpty().any { it.name.endsWith(".onnx") }} " +
            "association_entries=${AssociationManager.getCacheSize()} " +
            "avail_mb=${memory.availMem shr 20} total_mb=${memory.totalMem shr 20} " +
            "threshold_mb=${memory.threshold shr 20} low=${memory.lowMemory}"
    }

    /** 只看文件在不在、大小对不对，不做哈希校验：完整校验 400 多 MB 会占 CPU、挤掉别的缓存页。 */
    private fun installed(rime: File, id: String): Boolean = CandidateModelCatalog.models.firstOrNull { it.id == id }
        ?.versions?.any { version -> version.files.all { File(rime, it.name).let { file -> file.isFile && file.length() == it.sizeBytes } } } == true

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
}
