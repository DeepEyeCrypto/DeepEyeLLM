package com.deepeye.agent.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Resumable, chunked GGUF model download worker using OkHttp and Android WorkManager.
 * Runs as a Foreground Service with notification to prevent OS background cancellation.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeepEye-DownloadWorker"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_DEST_PATH = "dest_path"
        const val KEY_EXPECTED_CHECKSUM = "expected_checksum"

        const val PROGRESS_FLOAT = "progress_float"
        const val DOWNLOADED_BYTES = "downloaded_bytes"
        const val TOTAL_BYTES = "total_bytes"
        const val ERROR_MSG = "error_msg"
        const val BYTES_PER_SEC = "bytes_per_sec"

        private const val NOTIFICATION_CHANNEL_ID = "deepeye_downloads"
        private const val NOTIFICATION_ID = 4040
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val destPath = inputData.getString(KEY_DEST_PATH) ?: return@withContext Result.failure()
        val expectedChecksum = inputData.getString(KEY_EXPECTED_CHECKSUM) ?: ""

        val destFile = File(destPath)
        val tempFile = File(destPath + ".tmp")

        destFile.parentFile?.mkdirs()

        // Set foreground service notification
        runCatching {
            setForeground(createForegroundInfo(modelId, 0, 0L, 0L))
        }

        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        try {
            Log.d(TAG, "Starting download for $modelId. Resuming from byte $downloadedBytes...")

            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "DeepEyeLLM-Android/1.0")

            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP server error code ${response.code} for $downloadUrl")
            }

            val body = response.body ?: throw Exception("Empty response body from $downloadUrl")
            val contentLength = body.contentLength()
            val totalBytes = if (response.code == 206) downloadedBytes + contentLength else contentLength

            val input = body.byteStream()
            if (response.code != 206) {
                downloadedBytes = 0L
            }

            val buffer = ByteArray(1048576) // 1MB high-speed stream buffer
            var bytesRead: Int
            var lastReportTime = System.currentTimeMillis()

            val fos = FileOutputStream(tempFile, response.code == 206)
            val bos = BufferedOutputStream(fos, 1048576)

            try {
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) {
                        bos.close()
                        input.close()
                        Log.w(TAG, "Download worker cancelled by user or OS.")
                        return@withContext Result.retry()
                    }

                    bos.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 1500L || downloadedBytes == totalBytes) {
                        val elapsed = now - lastReportTime
                        lastReportTime = now
                        bos.flush()
                        val progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) else 0f
                        val bytesPerSec = if (elapsed > 0) (bytesRead * 1000L / elapsed) else 0L
                        
                        setProgress(
                            workDataOf(
                                PROGRESS_FLOAT to progress,
                                DOWNLOADED_BYTES to downloadedBytes,
                                TOTAL_BYTES to totalBytes,
                                BYTES_PER_SEC to bytesPerSec
                            )
                        )

                        runCatching {
                            val percentage = (progress * 100).toInt()
                            setForeground(createForegroundInfo(modelId, percentage, downloadedBytes, totalBytes))
                        }
                    }
                }
            } finally {
                runCatching { bos.flush(); bos.close() }
                runCatching { input.close() }
            }

            // Verify checksum if supplied
            if (expectedChecksum.isNotEmpty() && !verifyChecksum(tempFile, expectedChecksum)) {
                tempFile.delete()
                throw Exception("SHA-256 Checksum verification failed")
            }

            // Atomic rename to final GGUF destination
            if (!tempFile.renameTo(destFile)) {
                if (tempFile.exists() && destFile.exists()) {
                    destFile.delete()
                    tempFile.renameTo(destFile)
                }
            }

            Log.d(TAG, "Download completed successfully: ${destFile.absolutePath}")
            Result.success(workDataOf(KEY_DEST_PATH to destFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "Download error for $modelId", e)
            // Retry on transient network errors; fail permanently on content/checksum errors
            val isTransient = e is java.io.IOException || e is java.net.SocketTimeoutException
            if (isTransient && runAttemptCount < 5) {
                Log.w(TAG, "Transient error, will retry (attempt $runAttemptCount/5)")
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR_MSG to (e.message ?: "Unknown download failure")))
            }
        }
    }

    private fun createForegroundInfo(
        modelId: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ): ForegroundInfo {
        createNotificationChannel()

        val title = "Downloading $modelId"
        val downloadedMb = downloadedBytes / (1024 * 1024)
        val totalMb = totalBytes / (1024 * 1024)
        val contentText = if (totalMb > 0) "$progressPercent% ($downloadedMb MB / $totalMb MB)" else "Downloading..."

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Model Downloads"
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }
}
