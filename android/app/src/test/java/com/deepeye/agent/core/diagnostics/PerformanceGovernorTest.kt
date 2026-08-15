package com.deepeye.agent.core.diagnostics

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PerformanceGovernorTest {

    @Before
    fun setUp() {
        PerformanceGovernor.tpsRingBuffer.clear()
        PerformanceGovernor.latencyRingBuffer.clear()
        PerformanceGovernor.setThreadCount(4)
        PerformanceGovernor.setZeroBlur(false)
        PerformanceGovernor.updateKvCacheUsage(100f, 512f)
    }

    @Test
    fun testTelemetryRingBufferCapacity() {
        val ring = TelemetryRingBuffer(4)
        ring.push(10f)
        ring.push(20f)
        ring.push(30f)
        assertEquals(listOf(10f, 20f, 30f), ring.toList())

        ring.push(40f)
        ring.push(50f) // Should evict 10f
        val list = ring.toList()
        assertEquals(4, list.size)
        assertEquals(listOf(20f, 30f, 40f, 50f), list)
    }

    @Test
    fun testThermalStateEvaluation() {
        assertEquals(ThermalThrottlingState.NOMINAL, PerformanceGovernor.evaluateThermalState(35.0f))
        assertEquals(ThermalThrottlingState.MODERATE_WARM, PerformanceGovernor.evaluateThermalState(39.5f))
        assertEquals(ThermalThrottlingState.THROTTLING_ACTIVE, PerformanceGovernor.evaluateThermalState(43.0f))
        assertEquals(ThermalThrottlingState.CRITICAL_HEAT, PerformanceGovernor.evaluateThermalState(46.5f))
    }

    @Test
    fun testGovernorLoadSheddingActionsUnderThermalPressure() {
        PerformanceGovernor.recordTokenGeneration(
            ttftMs = 150L,
            tokensPerSecond = 32.0f,
            promptTps = 90.0f
        )
        // Simulate high thermal load and high KV usage
        PerformanceGovernor.updateKvCacheUsage(450f, 512f) // > 75%
        
        val actions = PerformanceGovernor.getRecommendedActions()
        assertTrue(actions.any { it.actionType == GovernorActionType.COMPACT_KV_CACHE })
    }

    @Test
    fun testThreadReduction() {
        PerformanceGovernor.setThreadCount(2)
        assertEquals(2, PerformanceGovernor.metrics.value.activeThreads)
    }
}
