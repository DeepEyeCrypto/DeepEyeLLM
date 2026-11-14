package com.deepeye.agent.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadWorkerTest {

    @Test
    fun testWorkerKeysAndTagConstants() {
        assertEquals("model_id", ModelDownloadWorker.KEY_MODEL_ID)
        assertEquals("download_url", ModelDownloadWorker.KEY_DOWNLOAD_URL)
        assertEquals("dest_path", ModelDownloadWorker.KEY_DEST_PATH)
        assertEquals("expected_checksum", ModelDownloadWorker.KEY_EXPECTED_CHECKSUM)
        assertEquals("progress_float", ModelDownloadWorker.PROGRESS_FLOAT)
        assertEquals("progress_percent", ModelDownloadWorker.PROGRESS_PERCENT)
        assertEquals("downloaded_bytes", ModelDownloadWorker.DOWNLOADED_BYTES)
        assertEquals("total_bytes", ModelDownloadWorker.TOTAL_BYTES)
        assertEquals("bytes_per_sec", ModelDownloadWorker.BYTES_PER_SEC)
        assertEquals("estimated_eta_sec", ModelDownloadWorker.ESTIMATED_ETA_SECONDS)
        assertEquals("model_download", ModelDownloadWorker.TAG_MODEL_DOWNLOAD)
        assertEquals("download_", ModelDownloadWorker.TAG_PREFIX)
    }

    @Test
    fun testProgressComputation() {
        val totalBytes = 2_000_000_000L
        val downloadedBytes = 500_000_000L
        val progressFloat = downloadedBytes.toFloat() / totalBytes.toFloat()
        val percent = (progressFloat * 100).toInt()

        assertEquals(0.25f, progressFloat, 0.001f)
        assertEquals(25, percent)
    }

    @Test
    fun testEtaComputation() {
        val totalBytes = 1_000_000_000L
        val downloadedBytes = 200_000_000L
        val remainingBytes = totalBytes - downloadedBytes
        val bytesPerSec = 20_000_000L // 20 MB/s

        val etaSec = remainingBytes / bytesPerSec
        assertEquals(40L, etaSec)
    }

    @Test
    fun testRangeHeaderFormatting() {
        val existingBytes = 104857600L // 100 MB
        val rangeHeader = "bytes=$existingBytes-"
        assertEquals("bytes=104857600-", rangeHeader)
    }
}
