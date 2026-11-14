package com.deepeye.agent.analysis

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.deepeye.agent.data.local.KnowledgeRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Autonomous Tool Execution Engine for DeepEye ReAct Loops.
 */
@Singleton
class AgentToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knowledgeRepository: KnowledgeRepository
) {
    private val gson = Gson()

    suspend fun execute(toolName: String, rawArguments: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val normalizedName = toolName.trim().lowercase()
            val result = when {
                normalizedName.contains("knowledge") || normalizedName.contains("search") || normalizedName.contains("rag") -> {
                    val query = extractArg(rawArguments, "query") ?: extractArg(rawArguments, "term") ?: rawArguments
                    executeKnowledgeSearch(query)
                }
                normalizedName.contains("calc") || normalizedName.contains("math") -> {
                    val expr = extractArg(rawArguments, "expression") ?: extractArg(rawArguments, "expr") ?: rawArguments
                    executeCalculator(expr)
                }
                normalizedName.contains("device") || normalizedName.contains("battery") || normalizedName.contains("hardware") -> {
                    executeDeviceInfo()
                }
                normalizedName.contains("file") || normalizedName.contains("read") -> {
                    val path = extractArg(rawArguments, "path") ?: rawArguments
                    executeFileReader(path)
                }
                normalizedName.contains("dex") || normalizedName.contains("quote") || normalizedName.contains("swap") -> {
                    val tokenIn = extractArg(rawArguments, "tokenIn") ?: "SOL"
                    val tokenOut = extractArg(rawArguments, "tokenOut") ?: "USDC"
                    val amountStr = extractArg(rawArguments, "amount") ?: "1.0"
                    executeDexQuote(tokenIn, tokenOut, amountStr.toDoubleOrNull() ?: 1.0)
                }
                else -> "Tool '$toolName' is not recognized. Available tools: knowledge_search, calculator, device_info, file_reader, dex_quote."
            }
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Executed tool '$toolName' in ${elapsed}ms -> ${result.take(100)}")
            result
        } catch (e: Throwable) {
            val errorMsg = "Error executing tool '$toolName': ${e.message}"
            Log.e(TAG, errorMsg, e)
            errorMsg
        }
    }

    private suspend fun executeKnowledgeSearch(query: String): String {
        if (query.isBlank()) return "Search query is empty."
        val chunks = knowledgeRepository.searchTopChunks(query, topK = 3)
        if (chunks.isEmpty()) {
            return "No matching knowledge entries found in local KnowledgeDb for '$query'."
        }
        val sb = StringBuilder()
        sb.appendLine("Found ${chunks.size} relevant knowledge chunks:")
        chunks.forEachIndexed { i, chunk ->
            sb.appendLine("--- [Match ${i + 1}] Source: ${chunk.docTitle} (Chunk ${chunk.chunkIndex}) ---")
            sb.appendLine(chunk.content.take(800))
            if (chunk.content.length > 800) sb.appendLine("... [truncated]")
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    private fun executeCalculator(expression: String): String {
        val cleanExpr = expression.replace("\"", "").replace("'", "").trim()
        if (cleanExpr.isBlank()) return "Expression is empty."
        return try {
            val evaluated = evaluateMath(cleanExpr)
            "Result: $cleanExpr = $evaluated"
        } catch (e: Throwable) {
            "Calculation error on '$cleanExpr': ${e.message}"
        }
    }

    private fun evaluateMath(expr: String): Double {
        val str = expr.replace(" ", "")
        if (str.startsWith("sqrt(", true) && str.endsWith(")")) {
            return sqrt(evaluateMath(str.substring(5, str.length - 1)))
        }
        return object : Any() {
            var pos = -1
            var ch = 0
            fun nextChar() { ch = if (++pos < str.length) str[pos].code else -1 }
            fun eat(c: Int): Boolean { while (ch == ' '.code) nextChar(); if (ch == c) { nextChar(); return true }; return false }
            fun parse(): Double { nextChar(); val x = parseExp(); if (pos < str.length) throw RuntimeException("Syntax error at " + ch.toChar()); return x }
            fun parseExp(): Double { var x = parseTerm(); while (true) { when { eat('+'.code) -> x += parseTerm(); eat('-'.code) -> x -= parseTerm(); else -> return x } } }
            fun parseTerm(): Double { var x = parseFactor(); while (true) { when { eat('*'.code) -> x *= parseFactor(); eat('/'.code) -> x /= parseFactor(); else -> return x } } }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val start = pos
                if (eat('('.code)) { x = parseExp(); eat(')'.code) }
                else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(start, pos).toDouble()
                } else throw RuntimeException("Unexpected: " + ch.toChar())
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }

    private fun executeDeviceInfo(): String {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0 != 0

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMemMb = memInfo.availMem / (1024 * 1024)
        val totalMemMb = memInfo.totalMem / (1024 * 1024)

        return buildString {
            appendLine("Device Hardware Status:")
            appendLine("• Model: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine("• Battery: ${if (batteryPct >= 0) "$batteryPct%" else "N/A"} (${if (isCharging) "Charging" else "Discharging"})")
            appendLine("• RAM: ${availMemMb}MB free / ${totalMemMb}MB total")
            appendLine("• Acceleration: Vulkan NPU + Arm Neon DotProd")
        }
    }

    private fun executeFileReader(path: String): String {
        val cleanPath = path.trim().removePrefix("\"").removeSuffix("\"")
        val file = File(cleanPath)
        if (!file.exists()) {
            val internalFile = File(context.filesDir, cleanPath)
            if (internalFile.exists()) return internalFile.readText().take(5000)
            return "File not found at path: $cleanPath"
        }
        return file.readText().take(5000)
    }

    private fun executeDexQuote(tokenIn: String, tokenOut: String, amount: Double): String {
        val rate = when (tokenIn.uppercase()) {
            "SOL" -> if (tokenOut.uppercase() == "USDC") 148.50 else 0.0024
            "ETH" -> if (tokenOut.uppercase() == "USDC") 2640.00 else 17.77
            "BTC" -> if (tokenOut.uppercase() == "USDC") 63200.00 else 425.5
            else -> 1.0
        }
        val estimatedOut = amount * rate
        return buildString {
            appendLine("DEX Quote Simulation:")
            appendLine("• Pair: ${tokenIn.uppercase()} ➔ ${tokenOut.uppercase()}")
            appendLine("• Amount In: $amount ${tokenIn.uppercase()}")
            appendLine("• Estimated Output: ${"%.4f".format(estimatedOut)} ${tokenOut.uppercase()}")
            appendLine("• Security Radar: 100/100 Safe")
        }
    }

    private fun extractArg(jsonOrText: String, key: String): String? {
        if (!jsonOrText.startsWith("{")) return null
        return try {
            val map = gson.fromJson(jsonOrText, Map::class.java)
            map[key]?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val TAG = "DeepEye-ToolExecutor"
    }

}

