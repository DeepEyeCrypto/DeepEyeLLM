package com.deepeye.agent.domain.engine

data class PerformanceStats(
    val ttftMs: Int,
    val tokensPerSec: Double
)
