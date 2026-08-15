package com.deepeye.agent.core.dex

import androidx.compose.runtime.Immutable

@Immutable
data class TokenPairQuote(
    val tokenInSymbol: String,
    val tokenInAddress: String,
    val tokenOutSymbol: String,
    val tokenOutAddress: String,
    val priceUsd: Double,
    val exchangeRate: Double,
    val liquidityUsd: Double,
    val volume24hUsd: Double,
    val priceChange24hPct: Double,
    val dexRouter: DexRouter = DexRouter.UNISWAP_V3
)

enum class DexRouter(val displayName: String, val network: String, val routerAddress: String) {
    UNISWAP_V3("Uniswap V3", "Ethereum / Base", "0xE592427A0AEce92De3Edee1F18E0157C05861564"),
    RAYDIUM("Raydium CLMM", "Solana", "CAMMCzo5YL8w4VFF8KVHrK22GGUsp5VTaW7grrKgrWqK"),
    JUPITER("Jupiter Aggregator", "Solana", "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"),
    PANCAKESWAP("PancakeSwap V3", "BNB Chain", "0x13f4EA83D0bd40E75C8222255bc855a974568Dd4")
}

@Immutable
data class SecurityAuditScore(
    val isHoneypot: Boolean,
    val buyTaxPct: Double,
    val sellTaxPct: Double,
    val isLpLocked: Boolean,
    val lpLockDurationDays: Int,
    val hasMintAuthority: Boolean,
    val isReentrancyClean: Boolean,
    val overallSafetyScore: Int // 0 to 100
) {
    val isSafeToTrade: Boolean
        get() = !isHoneypot && buyTaxPct <= 5.0 && sellTaxPct <= 5.0 && isLpLocked && isReentrancyClean && overallSafetyScore >= 70
}

enum class TradeAction {
    BUY,
    SELL,
    SWAP
}

enum class TradeExecutionStatus {
    SIMULATED,
    READY_FOR_SIGNATURE,
    EXECUTED,
    BLOCKED_BY_SAFETY_GATE,
    FAILED
}

@Immutable
data class DexTradeIntent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: TradeAction,
    val tokenIn: String,
    val tokenOut: String,
    val amountIn: Double,
    val estimatedAmountOut: Double,
    val maxSlippagePct: Double = 0.5,
    val quote: TokenPairQuote,
    val securityAudit: SecurityAuditScore,
    val status: TradeExecutionStatus = TradeExecutionStatus.SIMULATED,
    val statusMessage: String = "Simulated & Verified with Zero-Trust Safety Gates"
)
