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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Socket
import com.deepeye.agent.core.security.Sha256Verifier
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.roundToLong

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
const val ESTIMATED_ETA_SECONDS = "estimated_eta_sec"

        private const val NOTIFICATION_CHANNEL_ID = "deepeye_downloads"
        private const val NOTIFICATION_ID = 4040
    }

    // Tuned HTTP client for bulk GGUF downloads.
    // Android's default per-socket receive buffer (SO_RCVBUF, ~128–256 KiB) caps
    // TCP window throughput on high-bandwidth WLAN/mobile links, which makes
    // single-connection model downloads appear very slow. Requesting a large
    // receive buffer up front lets the kernel pipeline more data per RTT.
    private val okHttpClient: OkHttpClient by lazy {
        val tunedSocketFactory = object : SocketFactory() {
            override fun createSocket(host: String, port: Int): Socket = createTunedSocket()
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
                createTunedSocket()
            override fun createSocket(host: InetAddress, port: Int): Socket = createTunedSocket()
            override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
                createTunedSocket()
            override fun createSocket(): Socket = createTunedSocket()

            private fun createTunedSocket(): Socket = Socket().apply {
                receiveBufferSize = 4 * 1024 * 1024   // 4 MiB SO_RCVBUF for high throughput
                tcpNoDelay = true
                keepAlive = true
            }
        }

        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .dispatcher(Dispatcher().apply {
                // Default is 5 connections per host — raise it so the parallel
                // range-chunked downloader can actually open all its connections.
                maxRequestsPerHost = 16
                maxRequests = 32
            })
            .socketFactory(tunedSocketFactory)
            .build()
    }

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

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        try {
            Log.d(TAG, "Starting download for $modelId. Resuming from byte $existingBytes...")

            // If a legacy single-stream `.tmp` file already exists, finish it via the
            // single-stream path so existing progress isn't wasted; fresh downloads use
            // the fast parallel range-chunked path.
            if (existingBytes > 0L) {
                downloadSingleStream(modelId, downloadUrl, tempFile, existingBytes)
            } else {
                downloadParallelChunked(modelId, downloadUrl, tempFile)
            }

            if (isStopped) {
                Log.w(TAG, "Download cancelled by user or OS; partial progress preserved.")
                return@withContext Result.retry()
            }

            // Finalized file is in `tempFile`; verify checksum if one was supplied.
            if (expectedChecksum.isNotEmpty() && !Sha256Verifier.verify(tempFile, expectedChecksum)) {
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

        } catch (e: kotlinx.coroutines.CancellationException) {
            if (isStopped) {
                Log.w(TAG, "Download cancelled; partial progress preserved, will resume on retry.")
                return@withContext Result.retry()
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $modelId", e)
            // Retry on transient network errors; fail permanently on content/checksum errors
            val isTransient = e is IOException || e is java.net.SocketTimeoutException
            if (isTransient && runAttemptCount < 5) {
                Log.w(TAG, "Transient error, will retry (attempt $runAttemptCount/5)")
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR_MSG to (e.message ?: "Unknown download failure")))
            }
        }
    }

/**
     * Aggregates parallel-chunk progress and drives throttled progress/notification updates.
     * Thread-safe via an atomic counter; called from multiple chunk coroutines.
     */
    private inner class DownloadReporter(
        private val rModelId: String,
        private val rTotalBytes: Long,
        private val doReportProgress: suspend (androidx.work.Data) -> Unit,
        private val doReportForeground: suspend (Int, Long, Long) -> Unit
    ) {
        private val bytes = AtomicLong(0L)
        private var lastReportMs = 0L
        private var lastReportedBytes = 0L
        private var lastForegroundMs = 0L
        private var lastForegroundPercent = -1

        fun add(n: Long) { bytes.addAndGet(n) }

        suspend fun report(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val done = bytes.get()
            val elapsed = now - lastReportMs
            if (!force && elapsed < 1500L) return
            val delta = done - lastReportedBytes
            val bps = if (elapsed > 0L) delta * 1000L / elapsed else 0L
            val progress = if (rTotalBytes > 0L) done.toFloat() / rTotalBytes.toFloat() else 0f
            val percent = (progress * 100).toInt()
            lastReportMs = now
            lastReportedBytes = done
            val eta = if (bps > 0L && rTotalBytes > done) (rTotalBytes - done) / bps else -1
            Log.d(TAG, "progress model=$rModelId bytes=$done/${if (rTotalBytes > 0) rTotalBytes else -1L} ($percent%) speed=${bps / 1024} KiB/s [parallel] ETA=${if (eta >= 0) eta else "n/a"}s [parallel]")
            runCatching {
                doReportProgress(
                    workDataOf(
                        PROGRESS_FLOAT to progress,
                        DOWNLOADED_BYTES to done,
                        TOTAL_BYTES to rTotalBytes,
                        BYTES_PER_SEC to bps,
                        ESTIMATED_ETA_SECONDS to eta
                    )
                )
            }
            if (now - lastForegroundMs >= 5000L && (percent != lastForegroundPercent || done == rTotalBytes)) {
                lastForegroundMs = now
                lastForegroundPercent = percent
                runCatching { doReportForeground(percent, done, rTotalBytes) }
            }
        }
    }
/**
     * Legacy single-stream resume. Used only when a `.tmp` file already exists so the
     * partial download isn't discarded. Returns the server-reported total size.
     */
    private suspend fun downloadSingleStream(
        modelId: String,
        downloadUrl: String,
        tempFile: File,
        resumeBytes: Long
    ): Long {
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "DeepEyeLLM-Android/1.0")
            .header("Accept-Encoding", "identity")
        if (resumeBytes > 0L) requestBuilder.header("Range", "bytes=$resumeBytes-")

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw Exception("HTTP server error code ${response.code} for $downloadUrl")
        }
        val body = response.body ?: run { response.close(); throw Exception("Empty response body from $downloadUrl") }
        val contentLength = body.contentLength()
        var downloadedBytes = resumeBytes
        val totalBytes = if (response.code == 206) resumeBytes + contentLength else contentLength
        val totalDisplay = if (totalBytes > 0) totalBytes else -1L
        val input = body.byteStream()
        if (response.code != 206) downloadedBytes = 0L

        Log.d(TAG, "Headers model=$modelId http=${response.code} resumedBytes=$downloadedBytes totalBytes=$totalDisplay (single-stream)")

        val downloadStartMs = System.currentTimeMillis()
        val buffer = ByteArray(1048576)
        var bytesRead: Int
        var lastReportTimeMs = downloadStartMs
        var lastReportedBytes = downloadedBytes
        var lastForegroundMs = 0L
        var lastForegroundPercent = -1

        val fos = FileOutputStream(tempFile, response.code == 206)
        val bos = BufferedOutputStream(fos, 1048576)
        try {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) return@downloadSingleStream totalBytes
                bos.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                val now = System.currentTimeMillis()
                val elapsed = now - lastReportTimeMs
                if (elapsed >= 1500L || downloadedBytes == totalBytes) {
                    val delta = downloadedBytes - lastReportedBytes
                    val bps = if (elapsed > 0L) delta * 1000L / elapsed else 0L
                    val progress = if (totalBytes > 0L) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                    val percent = (progress * 100).toInt()
                    val eta = if (bps > 0L && totalBytes > downloadedBytes) (totalBytes - downloadedBytes) / bps else -1
                    Log.d(TAG, "progress model=$modelId bytes=$downloadedBytes/$totalDisplay ($percent%) speed=${bps / 1024} KiB/s ETA=${if (eta >= 0) eta else "n/a"}s")
                    bos.flush()
                    runCatching {
                        setProgress(
                            workDataOf(
                                PROGRESS_FLOAT to progress,
                                DOWNLOADED_BYTES to downloadedBytes,
                                TOTAL_BYTES to totalBytes,
                                BYTES_PER_SEC to bps,
                                ESTIMATED_ETA_SECONDS to eta
                            )
                        )
                    }
                    if (now - lastForegroundMs >= 5000L && (percent != lastForegroundPercent || downloadedBytes == totalBytes)) {
                        lastForegroundMs = now
                        lastForegroundPercent = percent
                        runCatching { setForeground(createForegroundInfo(modelId, percent, downloadedBytes, totalBytes)) }
                    }
                    lastReportTimeMs = now
                    lastReportedBytes = downloadedBytes
                }
            }
        } finally {
            runCatching { bos.flush(); bos.close() }
            runCatching { input.close() }
        }
        val e2eSec = (System.currentTimeMillis() - downloadStartMs) / 1000.0
        val avgKiBps = if (e2eSec > 0) (downloadedBytes / 1024.0 / e2eSec).roundToLong() else 0L
        Log.d(TAG, "complete model=$modelId bytes=$downloadedBytes/$totalDisplay avg=${avgKiBps} KiB/s elapsed=${e2eSec}s")
        return totalBytes
    }
/**
     * Fast download using up to 8 parallel HTTP Range connections. HF CDN returns 206,
     * so we pull non-overlapping byte ranges simultaneously, bypassing the per-connection
     * throughput cap (~2 MiB/s) that makes multi-GB GGUF downloads crawl.
     * Falls back to single-stream when the server doesn't honor Range.
     */
    private suspend fun downloadParallelChunked(modelId: String, downloadUrl: String, tempFile: File): Long = coroutineScope {
        // Probe Range support and learn the total size with a tiny ranged request.
        val probe = okHttpClient.newCall(
            Request.Builder().url(downloadUrl)
                .header("User-Agent", "DeepEyeLLM-Android/1.0")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-1023")
                .build()
        ).execute()
        val rangeSupported = probe.code == 206
        val totalBytes = probe.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            ?: probe.body?.contentLength() ?: -1L
        probe.close()

        if (!rangeSupported || totalBytes <= 0L) {
            Log.w(TAG, "Server does not honor Range for $modelId; falling back to single-stream.")
            return@coroutineScope downloadSingleStream(modelId, downloadUrl, tempFile, 0L)
        }

        // Scale chunk count: aim ~512 MiB per part, clamp to 8 concurrent connections.
        val maxConcurrency = 8
        val targetChunkBytes = 512L * 1024L * 1024L
        val nChunks = ((totalBytes / targetChunkBytes).toInt().coerceAtLeast(1))
            .coerceAtMost(maxConcurrency)
            .coerceAtLeast(4)
        val chunkSize = (totalBytes + nChunks - 1) / nChunks

        val parts = ArrayList<Triple<File, Long, Long>>(nChunks) // partFile, startByte, length
        for (i in 0 until nChunks) {
            val start = i.toLong() * chunkSize
            val len = min(chunkSize, totalBytes - start)
            parts.add(Triple(partFileFor(tempFile, i), start, len))
        }

        val reporter = DownloadReporter(
            modelId, totalBytes,
            doReportProgress = { setProgress(it) },
            doReportForeground = { p, b, t -> setForeground(createForegroundInfo(modelId, p, b, t)) }
        )

        // Resume: keep parts that are already complete, restart incomplete ones.
        val pending = ArrayList<Triple<File, Long, Long>>()
        parts.forEach { (file, start, len) ->
            if (file.exists() && file.length() == len) {
                reporter.add(len)
            } else {
                if (file.exists()) file.delete()
                pending.add(Triple(file, start, len))
            }
        }

        Log.d(TAG, "Parallel chunked download model=$modelId total=$totalBytes chunks=$nChunks concurrency=$maxConcurrency")

        val reporterJob = launch { while (isActive) { reporter.report(); delay(1500L) } }
        val chunkJobs = pending.map { (file, start, len) ->
            launch(Dispatchers.IO) { downloadChunk(modelId, downloadUrl, file, start, len, reporter) }
        }
        chunkJobs.forEach { it.join() }
        reporter.report(force = true)
        reporterJob.cancel()

        if (isStopped) return@coroutineScope totalBytes

        // Concatenate the ordered parts into the final temp file, then clean up parts.
        mergeParts(tempFile, nChunks)
        Log.d(TAG, "Merged $nChunks parts into ${tempFile.name}; mergedTotal=${tempFile.length()}")
        totalBytes
    }
/**
     * Downloads one byte-range [startByte, startByte+expectedLen) to [partFile].
     * Honours isStopped cooperatively; the caller decides retry/resume on partial write.
     */
    private fun downloadChunk(
        modelId: String,
        downloadUrl: String,
        partFile: File,
        startByte: Long,
        expectedLen: Long,
        reporter: DownloadReporter
    ) {
        val response = okHttpClient.newCall(
            Request.Builder().url(downloadUrl)
                .header("User-Agent", "DeepEyeLLM-Android/1.0")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=$startByte-${startByte + expectedLen - 1}")
                .build()
        ).execute()
        if (response.code != 206) {
            response.close()
            throw IOException("Server ignored Range for chunk @$startByte (HTTP ${response.code})")
        }
        val body = response.body ?: run { response.close(); throw IOException("Empty chunk body @$startByte") }
        val input = body.byteStream()
        val bos = BufferedOutputStream(FileOutputStream(partFile, false), 1048576)
        try {
            val buf = ByteArray(1048576)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                if (isStopped) {
                    bos.flush()
                    return@downloadChunk
                }
                bos.write(buf, 0, read)
                reporter.add(read.toLong())
            }
            bos.flush()
        } finally {
            runCatching { bos.close() }
            runCatching { input.close() }
        }
        if (!isStopped && partFile.length() != expectedLen) {
            partFile.delete()
            throw IOException("Chunk @$startByte incomplete: got ${partFile.length()}, expected $expectedLen")
        }
    }

    /** Concatenates the ordered part files into the final temp file, deleting parts as it goes. */
    private fun mergeParts(tempFile: File, partCount: Int) {
        val fos = FileOutputStream(tempFile, false)
        try {
            val buf = ByteArray(1048576)
            for (i in 0 until partCount) {
                val part = partFileFor(tempFile, i)
                if (!part.exists()) throw IOException("Missing download part $i; cannot finalize")
                part.inputStream().use { input ->
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) fos.write(buf, 0, read)
                }
                part.delete()
            }
        } finally {
            runCatching { fos.close() }
        }
    }

    /** Zero-padded part file name so OS directory listing and merge order match. */
    private fun partFileFor(tempFile: File, index: Int): File =
        File(tempFile.parentFile, tempFile.name + ".part" + String.format(Locale.US, "%03d", index))
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
}
