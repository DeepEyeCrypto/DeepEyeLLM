package com.deepeye.agent.core.diagnostics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdbDiagnosticsEngineTest {

    private lateinit var crashTriager: AdbCrashTriager

    @Before
    fun setUp() {
        crashTriager = AdbCrashTriager()
    }

    @Test
    fun testTriageSigsegvCrash() {
        val log = "DEBUG: *** *** ***\nFatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR) in libllama_jni.so"
        val report = crashTriager.triageLog(log)

        assertEquals(CrashSeverity.CRITICAL_JNI, report.severity)
        assertTrue(report.affectedModule.contains("llama-bridge.cpp"))
        assertTrue(report.suggestedPatch.contains("null checks"))
    }

    @Test
    fun testTriageOomPressure() {
        val log = "ActivityManager: LowMemoryKiller sending SIGKILL to process due to memory pressure"
        val report = crashTriager.triageLog(log)

        assertEquals(CrashSeverity.CRITICAL_OOM, report.severity)
        assertTrue(report.suggestedPatch.contains("KV cache"))
    }

    @Test
    fun testTriageChoreographerFrameDrops() {
        val log = "Choreographer: Skipped 48 frames! The application may be doing too much work on its main thread."
        val report = crashTriager.triageLog(log)

        assertEquals(CrashSeverity.WARNING, report.severity)
        assertTrue(report.affectedModule.contains("ChatViewModel"))
    }

    @Test
    fun testTriageNominalLog() {
        val log = "DeepEye: System initialized successfully on Vulkan compute backend."
        val report = crashTriager.triageLog(log)

        assertEquals(CrashSeverity.INFO, report.severity)
        assertTrue(report.title.contains("Normal Operational Telemetry"))
    }
}
