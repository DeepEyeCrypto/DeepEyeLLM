package com.deepeye.agent.core.datastore

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests validating inference settings constraints, KV-cache quantization modes,
 * and context size RAM budget limits.
 */
class InferenceSettingsValidationTest {

    @Test
    fun `default EngineSettings initializes with safe defaults`() {
        val settings = EngineSettings()
        assertTrue(settings.useGpu)
        assertEquals(-1, settings.selectedBackend)
        assertEquals(99, settings.gpuLayers)
        assertEquals(1024, settings.contextSize)
        assertEquals("FP16", settings.kvCacheQuant)
        assertEquals(0.7f, settings.temperature, 0.001f)
        assertEquals(0.9f, settings.topP, 0.001f)
    }

    @Test
    fun `validate KV-cache quantization reduction calculation`() {
        fun estimateKvCacheBytes(contextTokens: Int, layers: Int = 28, hiddenDim: Int = 2048, quant: String): Long {
            val bytesPerElement = when (quant) {
                "Q4_0" -> 0.5
                "Q8_0" -> 1.0
                else -> 2.0 // FP16
            }
            return (contextTokens.toDouble() * layers * hiddenDim * 2 * bytesPerElement).toLong()
        }

        val fp16Bytes = estimateKvCacheBytes(8192, quant = "FP16")
        val q8Bytes = estimateKvCacheBytes(8192, quant = "Q8_0")
        val q4Bytes = estimateKvCacheBytes(8192, quant = "Q4_0")

        assertEquals(fp16Bytes / 2, q8Bytes)
        assertEquals(fp16Bytes / 4, q4Bytes)
        assertTrue("Q4_0 KV-cache must be 75% smaller than FP16", q4Bytes < fp16Bytes)
    }

    @Test
    fun `validate context size RAM budget constraint`() {
        fun isContextSafeForRam(contextSize: Int, modelRamBytes: Long, totalDeviceRamBytes: Long): Boolean {
            val estimatedTotalBytes = modelRamBytes + (contextSize * 400_000L)
            val maxAllowedBytes = totalDeviceRamBytes * 0.80
            return estimatedTotalBytes <= maxAllowedBytes
        }

        val total8GbDeviceRam = 8L * 1024L * 1024L * 1024L
        val qwen05bRam = 600_000_000L
        val llama33bRam = 3_800_000_000L

        // Qwen 0.5B with 8192 context should easily pass on 8GB RAM
        assertTrue(isContextSafeForRam(8192, qwen05bRam, total8GbDeviceRam))

        // Hermes 3B with 4096 context should pass on 8GB RAM
        assertTrue(isContextSafeForRam(4096, llama33bRam, total8GbDeviceRam))

        // Hermes 3B with 16384 context on 4GB RAM should fail
        val lowRam4Gb = 4L * 1024L * 1024L * 1024L
        assertFalse(isContextSafeForRam(16384, llama33bRam, lowRam4Gb))
    }
}
