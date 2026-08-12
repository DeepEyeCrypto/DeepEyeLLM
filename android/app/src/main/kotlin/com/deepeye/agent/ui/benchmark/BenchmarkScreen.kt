package com.deepeye.agent.ui.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepeye.agent.benchmark.BenchmarkViewModel
import com.deepeye.agent.benchmark.BenchmarkUiState
import com.deepeye.agent.benchmark.AggregateBenchmarkResult
import com.deepeye.agent.benchmark.PromptBenchmarkResult
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.utils.PerformanceUtils
import com.deepeye.agent.ui.utils.UiLayoutMode
import com.deepeye.agent.ui.utils.currentUiLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode = currentUiLayoutMode()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = Color(0xDD070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LLM Performance Suite",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    NeonStatusBadge(
                        text = if (uiState.isRunning) "Running" else "Idle",
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
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BenchmarkControlCard(uiState, onRunBenchmark) }

        uiState.exportStatus?.let { status ->
            item {
                Text(status, color = Color(0xFF00E676), style = MaterialTheme.typography.bodyMedium)
            }
        }

        uiState.lastResult?.let { result ->
            item { ResultsSummaryCard(result, onExport) }
            item {
                Text(
                    "Prompt Metrics Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
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
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BenchmarkControlCard(uiState, onRunBenchmark)

        uiState.exportStatus?.let { status ->
            Text(status, color = Color(0xFF00E676), style = MaterialTheme.typography.bodyMedium)
        }

        uiState.lastResult?.let { result ->
            ResultsSummaryCard(result, onExport)
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
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
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
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Run the benchmark suite to see detailed prompt metrics here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkControlCard(
    uiState: BenchmarkUiState,
    onRunBenchmark: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Benchmark Suite Control", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.isRunning) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Benchmark progress: ${(uiState.progress * 100).toInt()}%" },
                    color = Color(0xFF00E5FF)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(uiState.currentPromptName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                Button(
                    onClick = onRunBenchmark,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start On-Device Benchmark Suite")
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
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Results Summary", style = MaterialTheme.typography.titleMedium, color = Color.White)
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color(0xFF00E5FF))
                }
            }
            Text("Model: ${result.modelName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg TTFT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.1f ms".format(result.avgTtftMs), style = MaterialTheme.typography.titleMedium, color = Color(0xFF00E5FF))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg Speed", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.1f tok/s".format(result.avgTokensPerSec), style = MaterialTheme.typography.titleMedium, color = Color(0xFF00E676))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Peak Memory", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.1f MB".format(result.peakMemoryMb), style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFB74D))
                }
            }
        }
    }
}

@Composable
private fun PromptMetricCard(p: PromptBenchmarkResult) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.promptName, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${p.totalTokens} tokens in ${p.totalTimeMs}ms", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.1f tok/s".format(p.tokensPerSec), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00E676))
                Text("TTFT: %.0f ms".format(p.ttftMs), style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E5FF))
            }
        }
    }
}
