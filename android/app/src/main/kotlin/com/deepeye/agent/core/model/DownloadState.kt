package com.deepeye.agent.core.model

/**
 * Sealed class representing atomic download states for GGUF models.
 */
sealed class DownloadState {
    object Idle : DownloadState()
    
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L
    ) : DownloadState()

    data class Paused(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()

    data class Error(
        val message: String
    ) : DownloadState()

    data class Completed(
        val filePath: String
    ) : DownloadState()
}
