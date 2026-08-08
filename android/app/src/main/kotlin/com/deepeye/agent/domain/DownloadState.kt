package com.deepeye.agent.domain

/**
 * Sealed class representing all possible states of a model download.
 * Replaces scattered EngineState download-related states with a type-safe hierarchy.
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesPerSec: Long = 0L) : DownloadState()
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Error(val reason: DownloadError, val retryable: Boolean) : DownloadState()
    data object Verifying : DownloadState()
    data object Completed : DownloadState()
}
