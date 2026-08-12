package com.deepeye.agent.benchmark

data class PromptBenchmarkResult(
    val promptName: String,
    val promptText: String,
    val ttftMs: Double,
    val tokensPerSec: Double,
    val totalTokens: Int,
    val totalTimeMs: Long,
    val peakMemoryMb: Double
)

data class AggregateBenchmarkResult(
    val modelName: String,
    val backendName: String,
    val promptResults: List<PromptBenchmarkResult>,
    val avgTtftMs: Double,
    val avgTokensPerSec: Double,
    val peakMemoryMb: Double,
    val timestamp: Long = System.currentTimeMillis()
)
