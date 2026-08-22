package com.deepeye.agent.ui.benchmark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.benchmark.AggregateBenchmarkResult
import com.deepeye.agent.benchmark.BenchmarkUiState
import com.deepeye.agent.benchmark.BenchmarkViewModel
import com.deepeye.agent.benchmark.PromptBenchmarkResult
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.TelemetrySpeedometer
import com.deepeye.agent.ui.theme.*
import com.deepeye.agent.ui.utils.PerformanceUtils
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    onBack: (() -> Unit)? = null,
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode = currentUiLayoutMode()

    Scaffold(
        containerColor = Color(0xFF070A12),
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = CyberCyan
                                )
                            }
                        }
                        Column {
                            Text(
                                "Edge LLM Benchmark Suite",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                "Hardware Latency, Decode Throughput & Memory Peak",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                        }
                    }
                    NeonStatusBadge(
                        text = if (uiState.isRunning) "Benchmarking" else "Engine Ready",
                        color = if (uiState.isRunning) CyberCyan else StatusSuccess,
                        isPulsing = uiState.isRunning
                    )
                }
            }
        }
    ) { padding ->
        if (layoutMode == UiLayoutMode.COMPACT) {
            // Single-column layout for phones
            BenchmarkCompactLayout(
                uiState = uiState,
                onRunBenchmark = { viewModel.runBenchmark() },
                onToggleComparisonMode = { viewModel.toggleComparisonMode() },
                onRunDiagnostics = { viewModel.runAdbHardwareDiagnostics() },
                onExport = { viewModel.exportResultsCsv() },
                modifier = Modifier.padding(padding)
            )
        } else {
            // Supporting Pane layout for tablets/desktop
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Primary pane: control + results summary
                BenchmarkPrimaryPane(
                    uiState = uiState,
                    onRunBenchmark = { viewModel.runBenchmark() },
                    onToggleComparisonMode = { viewModel.toggleComparisonMode() },
                    onRunDiagnostics = { viewModel.runAdbHardwareDiagnostics() },
                    onExport = { viewModel.exportResultsCsv() },
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                )
                // Supporting pane: prompt metrics breakdown
                BenchmarkDetailPane(
                    uiState = uiState,
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BenchmarkCompactLayout(
    uiState: BenchmarkUiState,
    onRunBenchmark: () -> Unit,
    onToggleComparisonMode: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TelemetrySpeedometer(
                tokensPerSecond = uiState.lastResult?.avgTokensPerSec ?: 0.0,
                engineName = uiState.lastResult?.backendName ?: "LiteRT NPU"
            )
        }

        item { BenchmarkControlCard(uiState, onRunBenchmark, onToggleComparisonMode) }

        item { AdbHardwareDiagnosticsCard(uiState, onRunDiagnostics) }

        uiState.exportStatus?.let { status ->
            item {
                Text(status, color = StatusSuccess, style = MaterialTheme.typography.bodyMedium)
            }
        }

        uiState.lastResult?.let { result ->
            item { ResultsSummaryCard(result, onExport) }
            
            if (uiState.isComparisonMode && uiState.comparisonResults.size > 1) {
                item { ComparisonResultsList(uiState.comparisonResults) }
            }
            
            item {
                Text(
                    "Prompt Metrics Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            items(
                items = result.promptResults,
                key = { it.promptName },
                contentType = { PerformanceUtils.ContentTypes.BENCHMARK_CARD }
            ) { p ->
                PromptMetricCard(p)
            }
        }
    }
}

@Composable
private fun BenchmarkPrimaryPane(
    uiState: BenchmarkUiState,
    onRunBenchmark: () -> Unit,
    onToggleComparisonMode: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TelemetrySpeedometer(
            tokensPerSecond = uiState.lastResult?.avgTokensPerSec ?: 0.0,
            engineName = uiState.lastResult?.backendName ?: "LiteRT NPU"
        )

        BenchmarkControlCard(uiState, onRunBenchmark, onToggleComparisonMode)

        AdbHardwareDiagnosticsCard(uiState, onRunDiagnostics)

        uiState.exportStatus?.let { status ->
            Text(status, color = StatusSuccess, style = MaterialTheme.typography.bodyMedium)
        }

        uiState.lastResult?.let { result ->
            ResultsSummaryCard(result, onExport)
        }
        
        if (uiState.isComparisonMode && uiState.comparisonResults.size > 1) {
            ComparisonResultsList(uiState.comparisonResults)
        }
    }
}

@Composable
private fun AdbHardwareDiagnosticsCard(
    uiState: BenchmarkUiState,
    onRunDiagnostics: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "ADB Hardware Diagnostic Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "On-Device NDK, KV-Cache & DEX Health Check",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan
                    )
                }

                uiState.diagnosticSummary?.let { diag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (diag.overallHealthScore >= 80) StatusSuccess.copy(alpha = 0.15f) else AmberAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${diag.overallHealthScore}/100 Health",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (diag.overallHealthScore >= 80) StatusSuccess else AmberAccent
                        )
                    }
                }
            }

            if (uiState.isRunningDiagnostics) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = CyberCyan,
                    trackColor = Color(0x3300E5FF)
                )
                Text(
                    "Running 5-phase on-device hardware telemetry...",
                    style = MaterialTheme.typography.bodySmall,
                    color = ThinkingMutedSlate
                )
            } else {
                CyberButton(
                    onClick = onRunDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyberCyan
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Autonomous Hardware Self-Test", fontWeight = FontWeight.Bold)
                }
            }

            uiState.diagnosticSummary?.let { diag ->
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    diag.tests.forEach { test ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x80151A29),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(test.name, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(test.details, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = ThinkingMutedSlate)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (test.isPassed) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = test.metricValue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (test.isPassed) StatusSuccess else StatusError
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkDetailPane(
    uiState: BenchmarkUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Text(
            "Prompt Metrics Breakdown",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val result = uiState.lastResult
        if (result != null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = result.promptResults,
                    key = { it.promptName },
                    contentType = { PerformanceUtils.ContentTypes.BENCHMARK_CARD }
                ) { p ->
                    PromptMetricCard(p)
                }
            }
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth(), tintColor = Color(0xCC0E1322)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = ThinkingMutedSlate, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Run the benchmark suite to see detailed prompt metrics here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ThinkingMutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkControlCard(
    uiState: BenchmarkUiState,
    onRunBenchmark: () -> Unit,
    onToggleComparisonMode: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Benchmark Suite Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Compare", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = uiState.isComparisonMode,
                        onCheckedChange = { onToggleComparisonMode() },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha=0.3f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.isRunning) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .semantics { contentDescription = "Benchmark progress: ${(uiState.progress * 100).toInt()}%" },
                    color = CyberCyan,
                    trackColor = Color(0x3300E5FF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Testing: ${uiState.currentPromptName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = ThinkingMutedSlate
                )
            } else {
                CyberButton(
                    onClick = onRunBenchmark,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = CyberCyan
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start On-Device Benchmark Battery", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResultsSummaryCard(
    result: AggregateBenchmarkResult,
    onExport: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Results Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Model: ${result.modelName}", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                }
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = CyberCyan)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg TTFT", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                    Text("%.1f ms".format(result.avgTtftMs), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CyberCyan)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg Speed", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                    Text("%.1f t/s".format(result.avgTokensPerSec), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = StatusSuccess)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Peak RAM", style = MaterialTheme.typography.labelSmall, color = ThinkingMutedSlate)
                    Text("%.1f MB".format(result.peakMemoryMb), style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = AmberAccent)
                }
            }
        }
    }
}

@Composable
private fun PromptMetricCard(p: PromptBenchmarkResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xCC0E1322),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.promptName, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${p.totalTokens} tokens in ${p.totalTimeMs}ms", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = ThinkingMutedSlate)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.1f t/s".format(p.tokensPerSec), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = StatusSuccess)
                Text("TTFT: %.0f ms".format(p.ttftMs), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
            }
        }
    }
}

@Composable
private fun ComparisonResultsList(results: List<AggregateBenchmarkResult>) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tintColor = Color(0xCC0E1322)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Model Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(14.dp))
            results.forEachIndexed { index, result ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.modelName, style = MaterialTheme.typography.labelMedium, color = Color.White)
                        Text(result.backendName, style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                    }
                    Text(
                        text = "%.1f t/s".format(result.avgTokensPerSec),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = StatusSuccess
                    )
                }
                if (index < results.lastIndex) {
                    Divider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}
