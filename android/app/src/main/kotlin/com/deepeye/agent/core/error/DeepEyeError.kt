package com.deepeye.agent.core.error

sealed class DeepEyeError(
    open val userMessage: String,
    open val technicalDetail: String,
    open val isRetryable: Boolean = false
) {
    // Engine errors
    data class EngineInitFailed(override val technicalDetail: String) : DeepEyeError("Failed to initialize AI engine", technicalDetail)
    data class InferenceFailed(override val technicalDetail: String) : DeepEyeError("AI processing failed", technicalDetail, isRetryable = true)
    data class ModelNotFound(val modelId: String) : DeepEyeError("Model '$modelId' not found on device", "Model file missing: $modelId", isRetryable = false)
    data class ModelLoadFailed(val modelId: String, override val technicalDetail: String) : DeepEyeError("Failed to load model '$modelId'", technicalDetail)
    data class InsufficientRam(val requiredGb: Long, val availableGb: Long) : DeepEyeError("Not enough RAM to load this model", "Required: ${requiredGb}GB, Available: ${availableGb}GB")

    // Download errors
    data class NetworkError(override val technicalDetail: String) : DeepEyeError("Download failed — check your connection", technicalDetail, isRetryable = true)
    data class DiskFull(val requiredBytes: Long) : DeepEyeError("Not enough storage space", "Required: ${requiredBytes / (1024*1024)} MB")
    data class ChecksumMismatch(val expected: String, val actual: String) : DeepEyeError("Downloaded file is corrupted — try again", "Expected: $expected, Got: $actual", isRetryable = true)
    data class DownloadCancelled(val modelId: String) : DeepEyeError("Download cancelled", "User cancelled download for $modelId")

    // Policy errors
    data class RestrictedFileType(val mimeType: String) : DeepEyeError("This file type is restricted", "Blocked MIME: $mimeType")
    data class OfflineModeRequired(val action: String) : DeepEyeError("Offline mode must be enabled for this action", "Action '$action' blocked by offline policy")
    data class FileTooLarge(val sizeBytes: Long, val maxBytes: Long) : DeepEyeError("File is too large for analysis", "Size: ${sizeBytes / (1024*1024)}MB, Max: ${maxBytes / (1024*1024)}MB")

    // File errors
    data class FileReadFailed(val path: String, override val technicalDetail: String) : DeepEyeError("Could not read file", technicalDetail)
    data class UnsupportedFormat(val format: String) : DeepEyeError("Unsupported file format: $format", "Format not recognized: $format")

    // RBAC / Access Control errors
    data class AccessDenied(val userId: String, val requiredPermission: String) : DeepEyeError(
        "You don't have permission to perform this action",
        "User '$userId' lacks permission: $requiredPermission",
        isRetryable = false
    )

    // Generic
    data class Unknown(override val technicalDetail: String) : DeepEyeError("Something went wrong", technicalDetail, isRetryable = true)
}
