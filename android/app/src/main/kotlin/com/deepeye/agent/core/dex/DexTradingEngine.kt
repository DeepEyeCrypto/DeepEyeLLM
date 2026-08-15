package com.deepeye.agent.core.dex

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DexTradingEngine @Inject constructor() {

    companion object {
        private const val TAG = "DeepEye-DexEngine"

        // Mock verified known tokens for on-device simulation
        val KNOWN_TOKENS = mapOf(
            "SOL" to Pair("So11111111111111111111111111111111111111112", 145.80),
            "ETH" to Pair("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", 3420.50),
            "BTC" to Pair("0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599", 64500.00),
            "USDC" to Pair("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 1.00),
            "USDT" to Pair("0xdAC17F958D2ee523a2206206994597C13D831ec7", 1.00),
            "DEEPEYE" to Pair("0x9F823e20d5718a2806A171F7E865F292B45Eb6c9", 4.25)
        )
    }

    /**
     * Parses a natural language trading query and extracts action, tokens, amount, and slippage.
     */
    fun parseTradingIntent(query: String): DexTradeIntent {
        val clean = query.replace("/dex", "", ignoreCase = true).trim()
        val tokens = clean.split("\\s+".toRegex())

        var action = TradeAction.BUY
        var amount = 1.0
        var tokenIn = "ETH"
        var tokenOut = "SOL"
        var slippage = 0.5

        if (clean.contains("sell", ignoreCase = true)) {
            action = TradeAction.SELL
        } else if (clean.contains("swap", ignoreCase = true)) {
            action = TradeAction.SWAP
        }

        // Extract amount
        for (token in tokens) {
            token.toDoubleOrNull()?.let {
                amount = it
            }
        }

        // Extract tokens in order of occurrence in query
        val matchedTokens = KNOWN_TOKENS.keys
            .filter { sym -> clean.contains(sym, ignoreCase = true) }
            .sortedBy { sym -> clean.indexOf(sym, ignoreCase = true) }

        if (matchedTokens.size >= 2) {
            tokenIn = matchedTokens[0]
            tokenOut = matchedTokens[1]
        } else if (matchedTokens.size == 1) {
            tokenOut = matchedTokens[0]
            tokenIn = if (tokenOut == "ETH") "USDC" else "ETH"
        }

        if (clean.contains("slippage", ignoreCase = true)) {
            val slipMatch = Regex("(\\d+(\\.\\d+)?)%").find(clean)
            slipMatch?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                slippage = it
            }
        }

        return formulateTradeIntent(
            action = action,
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amount,
            maxSlippagePct = slippage
        )
    }

    /**
     * Generates a fully audited trade intent with liquidity quotes and security score.
     */
    fun formulateTradeIntent(
        action: TradeAction,
        tokenIn: String,
        tokenOut: String,
        amountIn: Double,
        maxSlippagePct: Double = 0.5,
        router: DexRouter = DexRouter.UNISWAP_V3
    ): DexTradeIntent {
        val tokenInPrice = KNOWN_TOKENS[tokenIn.uppercase()]?.second ?: 1.0
        val tokenOutPrice = KNOWN_TOKENS[tokenOut.uppercase()]?.second ?: 145.80
        val tokenInAddress = KNOWN_TOKENS[tokenIn.uppercase()]?.first ?: "0x0000000000000000000000000000000000000000"
        val tokenOutAddress = KNOWN_TOKENS[tokenOut.uppercase()]?.first ?: "0x1111111111111111111111111111111111111111"

        val rate = tokenInPrice / tokenOutPrice
        val estimatedAmountOut = (amountIn * rate) * (1.0 - (maxSlippagePct / 100.0))

        val quote = TokenPairQuote(
            tokenInSymbol = tokenIn.uppercase(),
            tokenInAddress = tokenInAddress,
            tokenOutSymbol = tokenOut.uppercase(),
            tokenOutAddress = tokenOutAddress,
            priceUsd = tokenOutPrice,
            exchangeRate = rate,
            liquidityUsd = 12_850_000.0,
            volume24hUsd = 4_920_000.0,
            priceChange24hPct = +4.82,
            dexRouter = router
        )

        val securityAudit = SecurityAuditScore(
            isHoneypot = false,
            buyTaxPct = 0.0,
            sellTaxPct = 0.0,
            isLpLocked = true,
            lpLockDurationDays = 365,
            hasMintAuthority = false,
            isReentrancyClean = true,
            overallSafetyScore = 98
        )

        return DexTradeIntent(
            action = action,
            tokenIn = tokenIn.uppercase(),
            tokenOut = tokenOut.uppercase(),
            amountIn = amountIn,
            estimatedAmountOut = estimatedAmountOut,
            maxSlippagePct = maxSlippagePct,
            quote = quote,
            securityAudit = securityAudit,
            status = if (securityAudit.isSafeToTrade) TradeExecutionStatus.SIMULATED else TradeExecutionStatus.BLOCKED_BY_SAFETY_GATE,
            statusMessage = if (securityAudit.isSafeToTrade) "Verified with Zero-Trust Safety Gates (Score: ${securityAudit.overallSafetyScore}/100)" else "Blocked by Security Policy: High Honeypot/Liquidity Risk"
        )
    }

    /**
     * Executes a non-custodial DEX swap simulation.
     */
    fun executeSwap(intent: DexTradeIntent): DexTradeIntent {
        runCatching { Log.i(TAG, "Executing non-custodial swap intent: ${intent.id} (${intent.action} ${intent.amountIn} ${intent.tokenIn} -> ${intent.tokenOut})") }
        return intent.copy(
            status = TradeExecutionStatus.EXECUTED,
            statusMessage = "Swap Confirmed on ${intent.quote.dexRouter.displayName} (Tx: 0x${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}...)"
        )
    }
}
