package com.deepeye.agent.ui.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.deepeye.agent.core.hardware.HardwareFitEvaluator
import com.deepeye.agent.core.hardware.MemoryFitLevel
import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.core.model.ModelCapability
import com.deepeye.agent.core.model.ModelSpec
import com.deepeye.agent.core.model.ModelRegistry
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.repository.ModelRepository
import com.deepeye.agent.services.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Type-safe download status reflecting WorkManager background pipeline.
 */
sealed interface ModelDownloadStatus {
    data object Idle : ModelDownloadStatus
    data object Queued : ModelDownloadStatus
    data class Downloading(
        val progressPercent: Int,
        val progressFraction: Float,
        val speedKbps: Int = 0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val etaSeconds: Int = 0
    ) : ModelDownloadStatus
    data class Paused(
        val progressPercent: Int,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : ModelDownloadStatus
    data class Completed(val filePath: String) : ModelDownloadStatus
    data class Failed(val error: String) : ModelDownloadStatus
}

/**
 * Presentation item representing a local or catalog model in the Model Hub.
 */
data class ModelHubItem(
    val spec: ModelSpec,
    val downloadStatus: ModelDownloadStatus,
    val isInstalled: Boolean,
    val installedSizeBytes: Long = 0L,
    val isCompatible: Boolean = true,
    val memoryFitLevel: MemoryFitLevel = MemoryFitLevel.PERFECT_FIT,
    val isActiveModel: Boolean = false
)

/**
 * UI State for the Model Hub screen.
 */
data class ModelHubUiState(
    val models: List<ModelHubItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFamilyFilter: String = "ALL",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val totalDeviceRamGb: Int = 8,
    val activeModelId: String? = null
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val engineController: EngineController,
    private val modelRegistry: ModelRegistry,
    private val modelRepository: ModelRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "DeepEye-ModelManagerVM"
    }

    private val workManager = WorkManager.getInstance(context)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFamily = MutableStateFlow("ALL")
    val selectedFamily: StateFlow<String> = _selectedFamily.asStateFlow()

    private val _catalogSpecs = MutableStateFlow<List<ModelSpec>>(emptyList())
    private val _customModels = MutableStateFlow<List<ModelSpec>>(emptyList())

    private val _manualPausedState = MutableStateFlow<Map<String, ModelDownloadStatus.Paused>>(emptyMap())

    private val _snackbarEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val workInfosFlow = workManager.getWorkInfosByTagFlow(ModelDownloadWorker.TAG_MODEL_DOWNLOAD)

    private val filterStateFlow = combine(_searchQuery, _selectedFamily, engineController.engineStatus) { query, family, status ->
        Triple(query, family, status)
    }

    val uiState: StateFlow<ModelHubUiState> = combine(
        _catalogSpecs,
        _customModels,
        workInfosFlow,
        _manualPausedState,
        filterStateFlow
    ) { catalog, custom, workInfos, pausedMap, (query, family, _) ->
        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val diskFiles = modelsDir.listFiles() ?: emptyArray()
        val totalRamGb = modelRepository.getDeviceTotalRamGb()

        val allSpecs = (custom + catalog).distinctBy { it.id }
        val activeModelPath = engineController.getActiveEngineName()

        val items = allSpecs.map { spec ->
            val matchingFile = diskFiles.find {
                it.name.equals(spec.fileName, ignoreCase = true) ||
                it.nameWithoutExtension.equals(spec.fileName.substringBeforeLast('.'), ignoreCase = true) ||
                it.name.equals(spec.id, ignoreCase = true)
            }
            val isInstalled = matchingFile != null && matchingFile.length() > 500_000L
            val installedSize = matchingFile?.length() ?: 0L

            val modelWorkInfo = workInfos.find { info ->
                info.tags.contains("${ModelDownloadWorker.TAG_PREFIX}${spec.id}")
            }

            val downloadStatus: ModelDownloadStatus = when {
                isInstalled -> ModelDownloadStatus.Completed(matchingFile?.absolutePath ?: "")
                pausedMap.containsKey(spec.id) -> pausedMap.getValue(spec.id)
                modelWorkInfo != null -> mapWorkInfoToStatus(modelWorkInfo, spec)
                else -> {
                    val tempFile = File(modelsDir, "${spec.fileName}.tmp")
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        val pct = if (spec.sizeBytes > 0) ((tempFile.length().toFloat() / spec.sizeBytes) * 100).toInt() else 0
                        ModelDownloadStatus.Paused(pct, tempFile.length(), spec.sizeBytes)
                    } else {
                        ModelDownloadStatus.Idle
                    }
                }
            }

            val fitReport = HardwareFitEvaluator.evaluateModelFit(
                context = context,
                modelSizeBytes = spec.sizeBytes
            )
            val fitLevel = fitReport.fitLevel

            val isActive = matchingFile != null && (
                matchingFile.nameWithoutExtension.equals(activeModelPath, ignoreCase = true) ||
                matchingFile.name.equals(activeModelPath, ignoreCase = true)
            )

            ModelHubItem(
                spec = spec,
                downloadStatus = downloadStatus,
                isInstalled = isInstalled,
                installedSizeBytes = installedSize,
                isCompatible = fitLevel != MemoryFitLevel.HAZARDOUS_OOM_RISK,
                memoryFitLevel = fitLevel,
                isActiveModel = isActive
            )
        }

        val filteredItems = items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.spec.name.contains(query, ignoreCase = true) ||
                item.spec.family.contains(query, ignoreCase = true) ||
                item.spec.parameterCount.contains(query, ignoreCase = true)

            val matchesFamily = when (family.uppercase()) {
                "ALL" -> true
                "INSTALLED" -> item.isInstalled
                "GGUF" -> item.spec.backend == ModelBackend.GGUF_LLAMA_CPP
                "COLIBRI" -> item.spec.backend == ModelBackend.COLIBRI
                "LITERT" -> item.spec.backend == ModelBackend.LITERT
                else -> item.spec.family.equals(family, ignoreCase = true)
            }

            matchesQuery && matchesFamily
        }

        ModelHubUiState(
            models = filteredItems,
            searchQuery = query,
            selectedFamilyFilter = family,
            totalDeviceRamGb = totalRamGb,
            activeModelId = activeModelPath
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModelHubUiState(totalDeviceRamGb = modelRepository.getDeviceTotalRamGb())
    )

    init {
        refreshCatalog()
    }

    private fun mapWorkInfoToStatus(workInfo: WorkInfo, spec: ModelSpec): ModelDownloadStatus {
        val progressData = workInfo.progress
        val progressFloat = progressData.getFloat(ModelDownloadWorker.PROGRESS_FLOAT, 0f)
        val progressPercent = progressData.getInt(
            ModelDownloadWorker.PROGRESS_PERCENT,
            (progressFloat * 100).toInt()
        )
        val downloadedBytes = progressData.getLong(ModelDownloadWorker.DOWNLOADED_BYTES, 0L)
        val totalBytes = progressData.getLong(ModelDownloadWorker.TOTAL_BYTES, spec.sizeBytes)
        val bytesPerSec = progressData.getLong(ModelDownloadWorker.BYTES_PER_SEC, 0L)
        val etaSec = progressData.getInt(ModelDownloadWorker.ESTIMATED_ETA_SECONDS, 0)
        val speedKbps = (bytesPerSec / 1024L).toInt()

        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadStatus.Queued
            WorkInfo.State.RUNNING -> ModelDownloadStatus.Downloading(
                progressPercent = progressPercent,
                progressFraction = progressFloat,
                speedKbps = speedKbps,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                etaSeconds = etaSec
            )
            WorkInfo.State.SUCCEEDED -> {
                val destPath = workInfo.outputData.getString(ModelDownloadWorker.KEY_DEST_PATH) ?: ""
                ModelDownloadStatus.Completed(destPath)
            }
            WorkInfo.State.FAILED -> {
                val errorMsg = workInfo.outputData.getString(ModelDownloadWorker.ERROR_MSG) ?: "Download failed"
                ModelDownloadStatus.Failed(errorMsg)
            }
            WorkInfo.State.CANCELLED -> {
                val modelsDir = File(context.filesDir, "models")
                val tempFile = File(modelsDir, "${spec.fileName}.tmp")
                if (tempFile.exists() && tempFile.length() > 0L) {
                    val pct = if (spec.sizeBytes > 0) ((tempFile.length().toFloat() / spec.sizeBytes) * 100).toInt() else 0
                    ModelDownloadStatus.Paused(pct, tempFile.length(), spec.sizeBytes)
                } else {
                    ModelDownloadStatus.Idle
                }
            }
        }
    }

    fun downloadModel(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        val spec = (_catalogSpecs.value + _customModels.value).find { it.id == modelId } ?: return@launch
        val url = spec.downloadUrl
        if (url.isNullOrBlank()) {
            _snackbarEvent.emit("No download URL available for ${spec.name}")
            return@launch
        }

        _manualPausedState.update { it - modelId }

        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val destFile = File(modelsDir, spec.fileName)

        val workData = workDataOf(
            ModelDownloadWorker.KEY_MODEL_ID to modelId,
            ModelDownloadWorker.KEY_DOWNLOAD_URL to url,
            ModelDownloadWorker.KEY_DEST_PATH to destFile.absolutePath,
            ModelDownloadWorker.KEY_EXPECTED_CHECKSUM to (spec.sha256Hash ?: "")
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag(ModelDownloadWorker.TAG_MODEL_DOWNLOAD)
            .addTag("${ModelDownloadWorker.TAG_PREFIX}$modelId")
            .build()

        workManager.enqueueUniqueWork(
            "${ModelDownloadWorker.TAG_PREFIX}$modelId",
            ExistingWorkPolicy.REPLACE,
            request
        )

        Log.d(TAG, "Enqueued background download for model $modelId via WorkManager")
        _snackbarEvent.emit("Started background download: ${spec.name}")
    }

    fun pauseDownload(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        val spec = (_catalogSpecs.value + _customModels.value).find { it.id == modelId }
        workManager.cancelUniqueWork("${ModelDownloadWorker.TAG_PREFIX}$modelId")

        if (spec != null) {
            val modelsDir = File(context.filesDir, "models")
            val tempFile = File(modelsDir, "${spec.fileName}.tmp")
            val downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            val pct = if (spec.sizeBytes > 0) ((downloadedBytes.toFloat() / spec.sizeBytes) * 100).toInt() else 0
            _manualPausedState.update { it + (modelId to ModelDownloadStatus.Paused(pct, downloadedBytes, spec.sizeBytes)) }
            _snackbarEvent.emit("Paused download for ${spec.name}")
        }
    }

    fun resumeDownload(modelId: String) {
        downloadModel(modelId)
    }

    fun cancelDownload(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        val spec = (_catalogSpecs.value + _customModels.value).find { it.id == modelId }
        workManager.cancelUniqueWork("${ModelDownloadWorker.TAG_PREFIX}$modelId")
        _manualPausedState.update { it - modelId }

        if (spec != null) {
            val modelsDir = File(context.filesDir, "models")
            val tempFile = File(modelsDir, "${spec.fileName}.tmp")
            if (tempFile.exists()) tempFile.delete()

            for (i in 0..16) {
                val part = File(modelsDir, "${tempFile.name}.part" + String.format(java.util.Locale.US, "%03d", i))
                if (part.exists()) part.delete()
            }
            _snackbarEvent.emit("Cancelled download for ${spec.name}")
        }
    }

    fun deleteModel(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        val spec = (_catalogSpecs.value + _customModels.value).find { it.id == modelId } ?: return@launch
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, spec.fileName)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted model file: ${file.absolutePath}")
            modelRegistry.rescan()
            refreshCatalog(forceRefresh = true)
            _snackbarEvent.emit("Deleted ${spec.name}")
        }
    }

    fun selectModel(modelId: String) = viewModelScope.launch(Dispatchers.IO) {
        val spec = (_catalogSpecs.value + _customModels.value).find { it.id == modelId } ?: return@launch
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, spec.fileName)
        if (file.exists() && file.length() > 500_000L) {
            val (_, msg) = engineController.reinitializeWithModel(file.absolutePath)
            _snackbarEvent.emit(msg)
            refreshCatalog()
        } else {
            _snackbarEvent.emit("Model file not found on disk: ${spec.fileName}")
        }
    }

    fun importModel(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            var fileName = "imported_${System.currentTimeMillis()}.gguf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
            val destFile = File(modelsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Imported custom model: ${destFile.absolutePath} (${destFile.length()} bytes)")
            modelRegistry.rescan()
            refreshCatalog(forceRefresh = true)
            _snackbarEvent.emit("Imported model: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model from URI: $uri", e)
            _snackbarEvent.emit("Failed to import model: ${e.localizedMessage}")
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFamily(family: String) {
        _selectedFamily.value = family
    }

    fun refreshCatalog(forceRefresh: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        modelRegistry.rescan()
        val specs = modelRepository.fetchModelCatalog(forceRefresh = forceRefresh)
        _catalogSpecs.value = specs

        val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
        val filesOnDisk = modelsDir.listFiles() ?: emptyArray()
        val catalogFileNames = specs.map { it.fileName.lowercase() }.toSet()

        val custom = filesOnDisk.filter { file ->
            val name = file.name.lowercase()
            (name.endsWith(".gguf") || name.endsWith(".bin") || name.endsWith(".coli") || name.endsWith(".colibri")) &&
            !name.endsWith(".tmp") &&
            !name.contains(".part") &&
            name !in catalogFileNames &&
            file.length() > 500_000L
        }.map { file ->
            val isGguf = file.name.endsWith(".gguf")
            val isColibri = file.name.endsWith(".coli") || file.name.endsWith(".colibri")
            val backend = when {
                isGguf -> ModelBackend.GGUF_LLAMA_CPP
                isColibri -> ModelBackend.COLIBRI
                else -> ModelBackend.LITERT
            }
            ModelSpec(
                id = "custom_${file.name.hashCode()}",
                name = file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
                family = if (isColibri) "Colibri MoE" else if (isGguf) "GGUF" else "LiteRT",
                parameterCount = "${file.length() / (1024 * 1024 * 1024)}GB",
                backend = backend,
                quantization = com.deepeye.agent.core.model.Quantization.Q4_K_M,
                sizeBytes = file.length(),
                requiredRamBytes = (file.length() * 1.3).toLong(),
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                fileName = file.name,
                isDownloaded = true
            )
        }
        _customModels.value = custom
    }

    fun rescanLocalModels() {
        refreshCatalog(forceRefresh = true)
    }
}

