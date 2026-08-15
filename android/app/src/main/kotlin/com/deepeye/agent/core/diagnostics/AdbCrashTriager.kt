package com.deepeye.agent.core.diagnostics

import androidx.compose.runtime.Immutable
import javax.inject.Inject
import javax.inject.Singleton

enum class CrashSeverity {
    INFO,
    WARNING,
    CRITICAL_JNI,
    CRITICAL_OOM,
    CRITICAL_ANR
}

@Immutable
data class TriageReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val severity: CrashSeverity,
    val rootCause: String,
    val affectedModule: String,
    val suggestedPatch: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class AdbCrashTriager @Inject constructor() {

    /**
     * Triages raw logcat output or exception stack traces into actionable root causes and patches.
     */
    fun triageLog(rawLog: String): TriageReport {
        return when {
            rawLog.contains("SIGSEGV", ignoreCase = true) || rawLog.contains("tombstone", ignoreCase = true) -> {
                TriageReport(
                    title = "Native JNI Memory Fault (SIGSEGV)",
                    severity = CrashSeverity.CRITICAL_JNI,
                    rootCause = "Memory corruption or null pointer dereference inside libllama_jni.so / Vulkan compute shader buffer.",
                    affectedModule = "android/app/src/main/cpp/llama-bridge.cpp",
                    suggestedPatch = "Ensure null checks on llama_context and check tensor buffer memory bounds before calling nativeInitModel or nativeGenerateResponseStream."
                )
            }
            rawLog.contains("OutOfMemoryError", ignoreCase = true) || rawLog.contains("LowMemoryKiller", ignoreCase = true) || rawLog.contains("LMK", ignoreCase = true) -> {
                TriageReport(
                    title = "RAM Exhaustion / KV-Cache Out Of Memory",
                    severity = CrashSeverity.CRITICAL_OOM,
                    rootCause = "Model context length exceeded physical RAM limits on target SoC.",
                    affectedModule = "com.deepeye.agent.domain.engine.LlamaCppEngine",
                    suggestedPatch = "Reduce context length from 2048 to 1024 or enable 4-bit KV cache quantization (q4_0) in llama.cpp initialization params."
                )
            }
            rawLog.contains("Choreographer", ignoreCase = true) && rawLog.contains("Skipped", ignoreCase = true) -> {
                TriageReport(
                    title = "Main UI Thread Frame Drops (Choreographer)",
                    severity = CrashSeverity.WARNING,
                    rootCause = "Excessive recompositions on hot token streaming path or blocking I/O on Dispatchers.Main.",
                    affectedModule = "com.deepeye.agent.ui.chat.ChatViewModel",
                    suggestedPatch = "Verify 30ms token buffering in ChatViewModel and ensure all GGUF JNI invocations stay on Dispatchers.IO."
                )
            }
            rawLog.contains("ANR", ignoreCase = true) || rawLog.contains("Application Not Responding", ignoreCase = true) -> {
                TriageReport(
                    title = "Application Not Responding (ANR)",
                    severity = CrashSeverity.CRITICAL_ANR,
                    rootCause = "Long-running native inference execution blocked the Android Main thread.",
                    affectedModule = "com.deepeye.agent.domain.EngineController",
                    suggestedPatch = "Offload engineController.initialize() and nativeGenerateResponseStream into asynchronous CoroutineScope(Dispatchers.IO)."
                )
            }
            else -> {
                TriageReport(
                    title = "Normal Operational Telemetry",
                    severity = CrashSeverity.INFO,
                    rootCause = "All subsystems operational within nominal hardware bounds.",
                    affectedModule = "DeepEye Workstation Runtime",
                    suggestedPatch = "No patch required. Continue continuous hardware monitoring."
                )
            }
        }
    }
}
