package com.deepeye.agent.core.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.CancellationException

object ErrorMapper {

    fun map(throwable: Throwable): DeepEyeError = when (throwable) {
        is CancellationException -> DeepEyeError.DownloadCancelled("unknown")
        is UnknownHostException -> DeepEyeError.NetworkError("DNS resolution failed: ${throwable.message}")
        is SocketTimeoutException -> DeepEyeError.NetworkError("Connection timed out: ${throwable.message}")
        is IOException -> mapIOException(throwable)
        is OutOfMemoryError -> DeepEyeError.InsufficientRam(0, 0)
        else -> DeepEyeError.Unknown(throwable.message ?: throwable::class.simpleName ?: "Unknown error")
    }

    private fun mapIOException(e: IOException): DeepEyeError {
        val msg = e.message.orEmpty().lowercase()
        return when {
            msg.contains("no space") || msg.contains("disk full") -> DeepEyeError.DiskFull(0)
            msg.contains("permission") -> DeepEyeError.FileReadFailed("", e.message ?: "Permission denied")
            msg.contains("checksum") -> DeepEyeError.ChecksumMismatch("", "")
            else -> DeepEyeError.NetworkError(e.message ?: "IO error")
        }
    }

    fun mapWithContext(throwable: Throwable, context: String): DeepEyeError {
        val base = map(throwable)
        return DeepEyeError.Unknown("[$context] ${base.technicalDetail}")
    }
}
