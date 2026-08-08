package com.deepeye.agent.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.theme.PrimaryLocal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeScreen(viewModel: IdeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    var showNewFileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roo Code IDE (Phase 3)") },
                actions = {
                    IconButton(onClick = { showNewFileDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New File")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Workspace / File Explorer
            Text("Workspace Files", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (state.files.isEmpty()) {
                    item { Text("No files in workspace. Create one!", color = Color.Gray) }
                }
                items(state.files, key = { it.hashCode() }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectFile(file) }
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryLocal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(file.name, color = if (state.selectedFile == file) PrimaryLocal else Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Editor / Diff View
            Text("Editor: ${state.selectedFile?.name ?: "None"}", style = MaterialTheme.typography.titleMedium)
            GlassCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    if (state.diffPreview != null) {
                        item {
                            Text("Diff Preview", color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = state.diffPreview!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = viewModel::applyPatch, modifier = Modifier.fillMaxWidth()) {
                                Text("Apply Patch")
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = state.fileContent.ifEmpty { "Empty file." },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat Log
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                reverseLayout = true
            ) {
                items(state.chatLog.reversed(), key = { it.hashCode() }) { msg ->
                    val color = if (msg.startsWith("User")) Color.LightGray else PrimaryLocal
                    Text(msg, color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.isAgentTyping) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            // Vibe Chat Bar (Zero Latency)
            var currentPrompt by remember { mutableStateOf("") }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                ZeroLatencyTypingLayer(
                    modifier = Modifier.weight(1f),
                    placeholder = "Ask Roo Code to edit this file...",
                    onTextAvailable = { currentPrompt = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.submitPrompt(currentPrompt)
                        currentPrompt = "" // Local UI reset, although EditText content clearing needs explicit bridging if we wanted true bidirectional sync. For now, sending works.
                    },
                    enabled = !state.isAgentTyping && currentPrompt.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }

    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("test.kt") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(value = fileName, onValueChange = { fileName = it })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createNewFile(fileName)
                    showNewFileDialog = false
                }) { Text("Create") }
            }
        )
    }
}
