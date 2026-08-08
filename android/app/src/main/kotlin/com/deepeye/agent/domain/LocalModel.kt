package com.deepeye.agent.domain

enum class EngineState { NOT_DOWNLOADED, DOWNLOADING, PAUSED, DOWNLOADED, VERIFYING, READY, LOADING, LOADED, FAILED }
enum class DownloadError { NONE, NETWORK_ERROR, DISK_FULL, PERMISSION_DENIED, CHECKSUM_MISMATCH, UNKNOWN_ERROR }
enum class ModelCategory { SMALL_FAST, BALANCED, BIGGER_STRONGER, MULTIMODAL, ASSISTANT, CODE, EMBEDDING }

data class LocalModel(
    val id: String,
    val name: String,
    val publisher: String,
    val sizeString: String,
    val category: ModelCategory,
    val requiredRamGb: Int,
    val isChinese: Boolean = false,
    val downloadUrl: String,
    val fileName: String,
    val description: String = "",
    val expectedChecksum: String = "",
    val isSupportedOnDevice: Boolean = true,
    val engineState: EngineState = EngineState.NOT_DOWNLOADED,
    val lastError: DownloadError = DownloadError.NONE,
    val downloadProgress: Float = 0f
)
