package com.deepeye.agent.ui.file

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileAnalysisScreen(
    state: FileAnalysisUiState,
    onExplain: () -> Unit,
    onDebugDeeper: () -> Unit,
    onSaveReport: () -> Unit,
    onShare: () -> Unit,
    onSelectFinding: (FindingUi) -> Unit = {}
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("File Analysis") },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share report")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                error = state.error,
                modifier = Modifier.padding(padding)
            )
            else -> Content(
                state = state,
                onExplain = onExplain,
                onDebugDeeper = onDebugDeeper,
                onSaveReport = onSaveReport,
                onSelectFinding = onSelectFinding,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(error: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        ElevatedCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Analysis failed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Content(
    state: FileAnalysisUiState,
    onExplain: () -> Unit,
    onDebugDeeper: () -> Unit,
    onSaveReport: () -> Unit,
    onSelectFinding: (FindingUi) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FileHeaderCard(
                fileName = state.fileName,
                fileType = state.fileType,
                fileSize = state.fileSize,
                fileSource = state.fileSource
            )
        }

        item {
            SummaryCard(
                summary = state.summary,
                confidence = state.confidence
            )
        }

        if (state.findings.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Default.BugReport,
                    title = "Findings"
                )
            }
            items(state.findings, key = { "${it.title}_${it.location}" }) { finding ->
                FindingCard(
                    finding = finding,
                    onClick = { onSelectFinding(finding) }
                )
            }
        }

        if (state.suggestions.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Default.Info,
                    title = "Suggested fixes"
                )
            }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        state.suggestions.forEach { suggestion ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(suggestion)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        item {
            ActionRow(
                onExplain = onExplain,
                onDebugDeeper = onDebugDeeper,
                onSaveReport = onSaveReport
            )
        }
    }
}

@Composable
private fun FileHeaderCard(
    fileName: String,
    fileType: String,
    fileSize: String,
    fileSource: String
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, contentDescription = null)
                Text(fileName, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Type: $fileType")
            Text("Size: $fileSize")
            Text("Source: $fileSource")
        }
    }
}

@Composable
private fun SummaryCard(
    summary: String,
    confidence: String
) {
    ElevatedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(summary)
            Spacer(Modifier.height(12.dp))
            AssistChip(
                onClick = {},
                label = { Text("Confidence: $confidence") },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = "Fixes")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindingCard(
    finding: FindingUi,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Text(finding.title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(finding.description)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("Severity: ${finding.severity}") }
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text(finding.location) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ActionRow(
    onExplain: () -> Unit,
    onDebugDeeper: () -> Unit,
    onSaveReport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onExplain, modifier = Modifier.weight(1f)) {
                Text("Explain")
            }
            FilledTonalButton(onClick = onDebugDeeper, modifier = Modifier.weight(1f)) {
                Text("Debug deeper")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveReport, modifier = Modifier.weight(1f)) {
                Text("Save report")
            }
            OutlinedButton(onClick = onDebugDeeper, modifier = Modifier.weight(1f)) {
                Text("Share")
            }
        }
    }
}
