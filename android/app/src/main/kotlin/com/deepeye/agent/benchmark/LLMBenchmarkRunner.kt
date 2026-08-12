package com.deepeye.agent.benchmark

import android.content.Context
import android.os.Debug
import com.deepeye.agent.domain.EngineController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMBenchmarkRunner @Inject constructor(
    private val engineController: EngineController,
    @ApplicationContext private val context: Context
) {
    private val benchmarkPrompts = listOf(
        "Short QA" to "What is the capital of France?",
        "Code Gen" to "Write a quicksort function in Kotlin.",
        "Reasoning" to "Explain why 0.1 + 0.2 != 0.3 in IEEE 754 floating point standard."
    )

    suspend fun runSuite(
        iterationsPerPrompt: Int = 2,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): AggregateBenchmarkResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<PromptBenchmarkResult>()
        val totalSteps = benchmarkPrompts.size * iterationsPerPrompt
        var completedSteps = 0

        for ((name, text) in benchmarkPrompts) {
            var accumTtft = 0.0
            var accumTokSec = 0.0
            var accumTokens = 0
            var accumTime = 0L

            for (iter in 1..iterationsPerPrompt) {
                onProgress("Testing '$name' (iter $iter/$iterationsPerPrompt)", completedSteps.toFloat() / totalSteps)

                val startTime = System.currentTimeMillis()
                var firstTokenTime = 0L
                var tokenCount = 0

                val initialMem = getUsedMemoryMb()

                engineController.executeChatStream(text) { chunk ->
                    val now = System.currentTimeMillis()
                    if (tokenCount == 0) {
                        firstTokenTime = now
                    }
                    tokenCount += chunk.length
                }

                val endTime = System.currentTimeMillis()
                val totalTime = (endTime - startTime).coerceAtLeast(1)
                val ttft = if (firstTokenTime > 0) (firstTokenTime - startTime).toDouble() else totalTime.toDouble()
                val tokSec = if (totalTime > 0) (tokenCount.toDouble() * 1000.0 / totalTime.toDouble()) else 0.0

                accumTtft += ttft
                accumTokSec += tokSec
                accumTokens += tokenCount
                accumTime += totalTime
                completedSteps++
            }

            val avgTtft = accumTtft / iterationsPerPrompt
            val avgTokSec = accumTokSec / iterationsPerPrompt
            val avgTokens = accumTokens / iterationsPerPrompt
            val avgTime = accumTime / iterationsPerPrompt

            results.add(
                PromptBenchmarkResult(
                    promptName = name,
                    promptText = text,
                    ttftMs = avgTtft,
                    tokensPerSec = avgTokSec,
                    totalTokens = avgTokens,
                    totalTimeMs = avgTime,
                    peakMemoryMb = getUsedMemoryMb()
                )
            )
        }

        val overallAvgTtft = results.map { it.ttftMs }.average()
        val overallAvgTokSec = results.map { it.tokensPerSec }.average()
        val activeModel = engineController.getActiveEngineName()

        AggregateBenchmarkResult(
            modelName = activeModel,
            backendName = "Native Hardware Accelerated",
            promptResults = results,
            avgTtftMs = overallAvgTtft,
            avgTokensPerSec = overallAvgTokSec,
            peakMemoryMb = getUsedMemoryMb()
        )
    }

    private fun getUsedMemoryMb(): Double {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return usedBytes.toDouble() / (1024.0 * 1024.0)
    }
}
