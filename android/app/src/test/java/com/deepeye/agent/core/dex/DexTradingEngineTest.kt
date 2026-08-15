package com.deepeye.agent.core.dex

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DexTradingEngineTest {

    private lateinit var dexTradingEngine: DexTradingEngine

    @Before
    fun setUp() {
        dexTradingEngine = DexTradingEngine()
    }

    @Test
    fun testParseTradingIntentBasic() {
        val query = "/dex buy 0.5 ETH of SOL with 1% slippage"
        val intent = dexTradingEngine.parseTradingIntent(query)

        assertEquals(TradeAction.BUY, intent.action)
        assertEquals("ETH", intent.tokenIn)
        assertEquals("SOL", intent.tokenOut)
        assertEquals(0.5, intent.amountIn, 0.001)
        assertEquals(1.0, intent.maxSlippagePct, 0.001)
        assertTrue(intent.estimatedAmountOut > 0)
        assertTrue(intent.securityAudit.isSafeToTrade)
    }

    @Test
    fun testFormulateTradeIntentCalculatesCorrectOutput() {
        val intent = dexTradingEngine.formulateTradeIntent(
            action = TradeAction.SWAP,
            tokenIn = "ETH",
            tokenOut = "SOL",
            amountIn = 1.0,
            maxSlippagePct = 0.5
        )

        val ethPrice = 3420.50
        val solPrice = 145.80
        val expectedRate = ethPrice / solPrice
        val expectedOut = (1.0 * expectedRate) * 0.995

        assertEquals(expectedOut, intent.estimatedAmountOut, 0.01)
        assertEquals(TradeExecutionStatus.SIMULATED, intent.status)
    }

    @Test
    fun testExecuteSwapTransitionsToExecuted() {
        val intent = dexTradingEngine.parseTradingIntent("/dex swap 100 USDC for SOL")
        val executed = dexTradingEngine.executeSwap(intent)

        assertEquals(TradeExecutionStatus.EXECUTED, executed.status)
        assertTrue(executed.statusMessage.contains("Swap Confirmed"))
    }
}
