package com.deepeye.agent.ui.models

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.model.ModelRegistry
import com.deepeye.agent.core.model.ModelSpec
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.EngineState
import com.deepeye.agent.domain.LocalModel
import com.deepeye.agent.domain.ModelCategory
import com.deepeye.agent.domain.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val engineController: EngineController,
    private val modelRegistry: ModelRegistry,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _modelCatalog = MutableStateFlow<List<LocalModel>>(emptyList())
    val modelCatalog: StateFlow<List<LocalModel>> = _modelCatalog.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    
    private val _snackbarEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    init {
        refreshCatalog()
    }

    fun downloadModel(modelId: String) = viewModelScope.launch {
        val model = _modelCatalog.value.find { it.id == modelId } ?: return@launch

        _modelCatalog.update { list ->
            list.map { if (it.id == modelId) it.copy(engineState = EngineState.DOWNLOADING, downloadProgress = 0f) else it }
        }

        val destFile = File(engineController.context.filesDir, "models/${model.fileName}")
        val url = model.downloadUrl
        if (url.isNullOrBlank()) {
            Log.d("DeepEye", "{\"event\":\"download_failed\", \"model_id\":\"${model.id}\", \"error\":\"No download URL provided\"}")
            return@launch
        }

        Log.d("DeepEye", "{\"event\":\"download_started\", \"model_id\":\"${model.id}\"}")

        val workManager = androidx.work.WorkManager.getInstance(engineController.context)
        val workData = androidx.work.workDataOf(
            com.deepeye.agent.services.ModelDownloadWorker.KEY_MODEL_ID to modelId,
            com.deepeye.agent.services.ModelDownloadWorker.KEY_DOWNLOAD_URL to url,
            com.deepeye.agent.services.ModelDownloadWorker.KEY_DEST_PATH to destFile.absolutePath,
            com.deepeye.agent.services.ModelDownloadWorker.KEY_EXPECTED_CHECKSUM to ""
        )

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<com.deepeye.agent.services.ModelDownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .addTag("download_$modelId")
            .build()

        workManager.enqueueUniqueWork("download_$modelId", androidx.work.ExistingWorkPolicy.REPLACE, request)

        workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
            if (workInfo != null) {
                when (workInfo.state) {
                    androidx.work.WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getFloat(com.deepeye.agent.services.ModelDownloadWorker.PROGRESS_FLOAT, 0f)
                        _modelCatalog.update { list ->
                            list.map { if (it.id == modelId) it.copy(engineState = EngineState.DOWNLOADING, downloadProgress = progress) else it }
                        }
                    }
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        Log.d("DeepEye", "{\"event\":\"download_verified\", \"model_id\":\"$modelId\"}")
                        modelRegistry.rescan()
                        refreshCatalog()
                    }
                    androidx.work.WorkInfo.State.FAILED -> {
                        val err = workInfo.outputData.getString(com.deepeye.agent.services.ModelDownloadWorker.ERROR_MSG) ?: "Download failed"
                        Log.d("DeepEye", "{\"event\":\"download_failed\", \"model_id\":\"$modelId\", \"error\":\"$err\"}")
                        _modelCatalog.update { list ->
                            list.map { if (it.id == modelId) it.copy(engineState = EngineState.FAILED, downloadProgress = 0f) else it }
                        }
                    }
                    androidx.work.WorkInfo.State.CANCELLED -> {
                        Log.d("DeepEye", "{\"event\":\"download_cancelled\", \"model_id\":\"$modelId\"}")
                        _modelCatalog.update { list ->
                            list.map { if (it.id == modelId) it.copy(engineState = EngineState.NOT_DOWNLOADED, downloadProgress = 0f) else it }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun pauseDownload(modelId: String) {
        val workManager = androidx.work.WorkManager.getInstance(engineController.context)
        workManager.cancelUniqueWork("download_$modelId")
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        _modelCatalog.update { list ->
            list.map { if (it.id == modelId) it.copy(engineState = EngineState.PAUSED) else it }
        }
        Log.d("DeepEye", "{\"event\":\"download_paused\", \"model_id\":\"$modelId\"}")
    }

    fun resumeDownload(modelId: String) {
        Log.d("DeepEye", "{\"event\":\"download_resumed\", \"model_id\":\"$modelId\"}")
        downloadModel(modelId)
    }

    fun cancelDownload(modelId: String) {
        val model = _modelCatalog.value.find { it.id == modelId }
        val workManager = androidx.work.WorkManager.getInstance(engineController.context)
        workManager.cancelUniqueWork("download_$modelId")
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)

        if (model != null) {
            val tempFile = File(engineController.context.filesDir, "models/${model.fileName}.tmp")
            if (tempFile.exists()) tempFile.delete()
        }

        _modelCatalog.update { list ->
            list.map { if (it.id == modelId) it.copy(engineState = EngineState.NOT_DOWNLOADED, downloadProgress = 0f) else it }
        }
    }

    fun deleteModel(modelId: String) {
        val model = _modelCatalog.value.find { it.id == modelId } ?: return
        val destFile = File(engineController.context.filesDir, "models/${model.fileName}")
        if (destFile.exists()) destFile.delete()
        modelRegistry.rescan()
        refreshCatalog()
    }

    fun selectModel(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("DeepEye", "{\"event\":\"select_model_called\", \"id\":\"$modelId\"}")
        val model = _modelCatalog.value.find { it.id == modelId }
        val modelsDir = File(engineController.context.filesDir, "models")
        var destFile: File? = null

        if (model != null) {
            val direct = File(modelsDir, model.fileName)
            if (direct.exists() && direct.length() > 1_000_000L) {
                destFile = direct
            }
        }

        if (destFile == null) {
            val directId = File(modelsDir, modelId)
            if (directId.exists() && directId.length() > 1_000_000L) {
                destFile = directId
            }
        }

        if (destFile == null) {
            val filesOnDisk = modelsDir.listFiles() ?: emptyArray()
            destFile = filesOnDisk.find {
                it.name.equals(modelId, ignoreCase = true) ||
                it.nameWithoutExtension.equals(modelId, ignoreCase = true) ||
                (model != null && it.name.equals(model.fileName, ignoreCase = true)) ||
                (model != null && it.nameWithoutExtension.equals(model.fileName.substringBeforeLast('.'), ignoreCase = true))
            }
        }

        if (destFile != null && destFile.exists() && destFile.length() > 1_000_000L) {
            Log.d("DeepEye", "{\"event\":\"reinitializing_with_model\", \"path\":\"${destFile.absolutePath}\"}")
            val (_, msg) = engineController.reinitializeWithModel(destFile.absolutePath)
            _snackbarEvent.tryEmit(msg)
            refreshCatalog()
        } else if (model != null && model.downloadUrl.isNotBlank()) {
            _snackbarEvent.tryEmit("Starting download for ${model.name}...")
            downloadModel(modelId)
        } else {
            _snackbarEvent.tryEmit("No local file for model. Tap 'Import Model' to load a .bin or .gguf binary.")
        }
    }

    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "custom_model_${System.currentTimeMillis()}.bin"
            val destFile = File(engineController.context.filesDir, "models/$fileName")
            val parentDir = destFile.parentFile
            if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()

            engineController.context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            modelRegistry.rescan()
            refreshCatalog()

            val (_, msg) = engineController.reinitializeWithModel(destFile.absolutePath)
            _snackbarEvent.tryEmit("Imported & Activated: $fileName")
        } catch (e: Exception) {
            Log.e("ModelCatalogVM", "Error importing model: ${e.message}", e)
            _snackbarEvent.tryEmit("Import Failed: ${e.message}")
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            val cursor = engineController.context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) return it.getString(displayNameIndex)
                }
            }
            uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun isSupportedFormat(fileName: String): Boolean {
        return fileName.endsWith(".bin") || fileName.endsWith(".tflite") || fileName.endsWith(".gguf")
    }

    private fun mapSpecsToLocalModels(specs: List<ModelSpec>): List<LocalModel> {
        val modelsDir = File(engineController.context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val filesOnDisk = modelsDir.listFiles() ?: emptyArray()

        val catalogModels = specs.map { spec ->
            val fileOnDisk = filesOnDisk.find {
                it.name.equals(spec.fileName, ignoreCase = true) ||
                it.nameWithoutExtension.equals(spec.fileName.substringBeforeLast('.'), ignoreCase = true) ||
                it.name.equals(spec.id, ignoreCase = true)
            }
            val isDownloaded = fileOnDisk != null && fileOnDisk.length() > 1_000_000L
            val isLoadable = isSupportedFormat(spec.fileName)
            val sizeStr = if (isDownloaded) "${fileOnDisk!!.length() / (1024 * 1024)} MB" else spec.parameterCount

            val engineState = when {
                isDownloaded && isLoadable -> EngineState.READY
                isDownloaded && !isLoadable -> EngineState.DOWNLOADED
                else -> EngineState.NOT_DOWNLOADED
            }

            LocalModel(
                id = spec.id,
                name = spec.name,
                publisher = spec.family,
                sizeString = sizeStr,
                category = ModelCategory.BALANCED,
                requiredRamGb = (spec.requiredRamMb / 1024).toInt(),
                isChinese = false,
                downloadUrl = spec.downloadUrl ?: "",
                fileName = fileOnDisk?.name ?: spec.fileName,
                isSupportedOnDevice = isLoadable,
                engineState = engineState,
                downloadProgress = if (isDownloaded) 1f else 0f
            )
        }

        val mappedFileNames = catalogModels.filter { it.engineState == EngineState.READY }.map { it.fileName.lowercase() }.toSet()
        val customModelsOnDisk = filesOnDisk.filter { it.name.lowercase() !in mappedFileNames && isSupportedFormat(it.name) && it.length() > 1_000_000L }.map { file ->
            LocalModel(
                id = "custom_${file.name.hashCode()}",
                name = file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
                publisher = if (file.name.endsWith(".gguf")) "GGUF Local" else "LiteRT Local",
                sizeString = "${file.length() / (1024 * 1024)} MB",
                category = ModelCategory.BALANCED,
                requiredRamGb = 2,
                isChinese = false,
                downloadUrl = "",
                fileName = file.name,
                engineState = EngineState.READY,
                downloadProgress = 1f
            )
        }

        return customModelsOnDisk + catalogModels
    }

    fun refreshCatalog() = viewModelScope.launch(Dispatchers.IO) {
        Log.d("DeepEye", "{\"event\":\"catalog_refresh_started\"}")
        modelRegistry.rescan()
        val specs = modelRepository.fetchModelCatalog()
        _modelCatalog.value = mapSpecsToLocalModels(specs)
        Log.d("DeepEye", "{\"event\":\"catalog_refresh_completed\", \"count\":${_modelCatalog.value.size}}")
    }

    fun rescanLocalModels() = viewModelScope.launch(Dispatchers.IO) {
        refreshCatalog()
    }
}
