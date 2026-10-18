package com.kingzcheung.xime.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.model.ModelManager
import com.kingzcheung.xime.model.ModelRuntime
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SmartPredictionUiState(
    val isEnabled: Boolean = false,
    val isInitialized: Boolean = false,
    val cacheSize: Int = 0,
    val observations: Long = 0,
    val profileSize: Int = 0,
    val learningStatus: String = "",
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadStatus: String = "",
    val hasModel: Boolean = false,
    val toastMessage: String? = null
)

class SmartPredictionSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private var modelOperation: kotlinx.coroutines.Job? = null
    
    private val _uiState = MutableStateFlow(SmartPredictionUiState(
        isEnabled = SettingsPreferences.isSmartPredictionEnabled(context),
        isInitialized = AssociationManager.isInitialized()
    ))
    val uiState: StateFlow<SmartPredictionUiState> = _uiState.asStateFlow()
    
    init {
        checkModelState()
        loadCacheSize()
        validateModelState()
        loadRemoteModels()
        viewModelScope.launch {
            ModelManager.installedRevision.collect { checkModelState() }
        }
        viewModelScope.launch {
            ModelManager.downloadStates.collect { states ->
                val state = states[SettingsPreferences.getPredictionSelectedModel(context)]
                val progress = state as? com.kingzcheung.xime.model.ModelDownloadState.Downloading
                _uiState.update { it.copy(isDownloading = progress != null,
                    downloadProgress = progress?.progress ?: 0f,
                    downloadStatus = (state as? com.kingzcheung.xime.model.ModelDownloadState.Error)?.message.orEmpty()) }
            }
        }
    }

    private fun loadRemoteModels() {
        viewModelScope.launch {
            ModelManager.loadFromRemote(context)
        }
    }
    
    private fun checkModelState() {
        val modelId = SettingsPreferences.getPredictionSelectedModel(context)
        val hasModel = ModelManager.isModelReady(context, modelId)
        _uiState.update { it.copy(hasModel = hasModel) }
    }
    
    private fun loadCacheSize() {
        viewModelScope.launch {
            try {
                updateLearningCounts()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.update { it.copy(learningStatus = "读取失败：${error.message}") }
            }
        }
    }

    private suspend fun updateLearningCounts() {
        val data = withContext(Dispatchers.IO) { AssociationManager.learningData(context) }
        _uiState.update { it.copy(cacheSize = data.uniqueSequences, observations = data.observations,
            profileSize = data.continuations.size) }
    }

    private fun learningOperation(success: String, block: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, learningStatus = "处理中…") }
            try {
                withContext(Dispatchers.IO) { block() }
                updateLearningCounts()
                _uiState.update { it.copy(learningStatus = success, toastMessage = success) }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                val message = "操作失败：${error.message}"
                _uiState.update { it.copy(learningStatus = message, toastMessage = message) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun exportLearningData(uri: android.net.Uri) = learningOperation("已导出学习文件") {
        val data = AssociationManager.learningData(context)
        AssociationManager.saveUserData()
        val output = checkNotNull(context.contentResolver.openOutputStream(uri)) { "无法写入所选位置" }
        output.bufferedWriter().use { it.write(data.encode()) }
    }

    fun importLearningData(uri: android.net.Uri) = learningOperation("已导入；导入前数据已备份") {
        val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选文件" }
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= com.kingzcheung.xime.association.PersonalLearningData.MAX_BYTES) { "学习文件超过 32 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= com.kingzcheung.xime.association.PersonalLearningData.MAX_BYTES) { "学习文件超过 32 MB" }
        val data = com.kingzcheung.xime.association.PersonalLearningData.decode(bytes.toString(Charsets.UTF_8))
        AssociationManager.importLearningData(context, data)
    }
    
    private fun validateModelState() {
        // The bundled public prior is available without a downloaded neural model.
    }

    fun selectModel(id: String) {
        modelOperation?.cancel()
        SettingsPreferences.setPredictionSelectedModel(context, id)
        checkModelState()
        AssociationManager.release()
        _uiState.update { it.copy(isInitialized = false, isLoading = false) }
        if (_uiState.value.isEnabled) loadModel()
    }

    fun setEnabled(enabled: Boolean) {
        checkModelState()
        SettingsPreferences.setSmartPredictionEnabled(context, enabled)
        _uiState.update { it.copy(isEnabled = enabled) }
        
        if (enabled && !_uiState.value.isInitialized) {
            loadModel()
            if (_uiState.value.isInitialized) {
                ModelRuntime.keepWarm("predictive_text")
            }
        } else if (!enabled) {
            ModelRuntime.releaseWarm("predictive_text")
            releaseModel()
        }
    }
    
    private fun loadModel() {
        modelOperation?.cancel()
        modelOperation = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val success = withContext(Dispatchers.IO) {
                AssociationManager.initialize(context)
            }
            
            _uiState.update { it.copy(
                isInitialized = success,
                isLoading = false
            )}
            
            if (!success || _uiState.value.hasModel && !com.kingzcheung.xime.association.OnnxAssociationEngine.isInitialized()) {
                _uiState.update { it.copy(toastMessage = "模型加载失败，当前使用基础联想；请检查模型文件") }
            }
        }
    }
    
    private fun releaseModel() {
        modelOperation?.cancel()
        AssociationManager.release()
        _uiState.update { it.copy(isInitialized = false, isLoading = false) }
    }
    
    fun saveUserData() {
        learningOperation("已保存到本机；导出请使用“导出文件”") { AssociationManager.saveUserData() }
    }
    
    fun refreshCacheSize() {
        learningOperation("已刷新统计") { }
    }
    
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
    
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
