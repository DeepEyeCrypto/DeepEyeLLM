package com.deepeye.agent.ui.rag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.core.memory.MemoryEntity
import com.deepeye.agent.ui.components.CyberChip
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.MemoryInspectorView
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.*

data class RagDocument(
    val id: String,
    val title: String,
    val chunkCount: Int,
    val status: String,
    val isIndexed: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isAddDocDialogOpen by remember { mutableStateOf(false) }
    var selectedDocForDetails by remember { mutableStateOf<RagDocument?>(null) }
    
    val sampleDocs = remember {
        mutableStateListOf(
            RagDocument("1", "Architecture_Decisions.md", 42, "Indexed", true),
            RagDocument("2", "DeepEye_Security_Model.pdf", 128, "Indexed", true),
            RagDocument("3", "Operations_Runbook.txt", 19, "Processing...", false)
        )
    }

    val persistentMemories = remember {
        mutableStateListOf(
            MemoryEntity(content = "Verify liquidity lock >= 90 days on all Uniswap/Sushiswap pool audits", tags = "crypto,security"),
            MemoryEntity(content = "Default local inference engine preference: LiteRT with QNN hardware delegate", tags = "hardware,npu"),
            MemoryEntity(content = "Strict zero-cloud fallback mode for sensitive source files (*.pem, *.key, *.env)", tags = "policy,privacy")
        )
    }

    // Add Document Dialog
    if (isAddDocDialogOpen) {
        var docNameInput by remember { mutableStateOf("") }
        var docChunksInput by remember { mutableStateOf("32") }

        AlertDialog(
            onDismissRequest = { isAddDocDialogOpen = false },
            title = { Text("Import Knowledge Document", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Import local document into sovereign on-device vector index (384-dim embeddings).",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThinkingMutedSlate
                    )
                    OutlinedTextField(
                        value = docNameInput,
                        onValueChange = { docNameInput = it },
                        label = { Text("Document Filename") },
                        placeholder = { Text("e.g. SmartContract_Audit.sol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = docChunksInput,
                        onValueChange = { docChunksInput = it },
                        label = { Text("Estimated Chunks") },
                        placeholder = { Text("32") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (docNameInput.isNotBlank()) {
                            val chunks = docChunksInput.toIntOrNull() ?: 24
                            sampleDocs.add(
                                0,
                                RagDocument(
                                    id = System.currentTimeMillis().toString(),
                                    title = docNameInput.trim(),
                                    chunkCount = chunks,
                                    status = "Indexed",
                                    isIndexed = true
                                )
                            )
                            isAddDocDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Index Document", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddDocDialogOpen = false }) {
                    Text("Cancel", color = ThinkingMutedSlate)
                }
            },
            containerColor = Color(0xF20E1322)
        )
    }

    // Document Details Modal
    selectedDocForDetails?.let { doc ->
        AlertDialog(
            onDismissRequest = { selectedDocForDetails = null },
            title = {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Vector Index Metadata",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                    Text("• Status: ${doc.status}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("• Vector Embeddings: ${doc.chunkCount} chunks (384-dimensional)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("• Storage: On-device SQLite Vector Table", color = ThinkingMutedSlate, style = MaterialTheme.typography.bodySmall)
                    Text("• Cosine Distance Threshold: 0.72", color = ThinkingMutedSlate, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedDocForDetails = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sampleDocs.removeAll { it.id == doc.id }
                        selectedDocForDetails = null
                    }
                ) {
                    Text("Delete from Index", color = StatusError)
                }
            },
            containerColor = Color(0xF20E1322)
        )
    }

    Scaffold(
        containerColor = Color(0xFF070A12),
        topBar = {
            Surface(
                color = Color(0xF2070A12),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Knowledge & Memory Hub",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% On-Device Vector Index & Working Context Ledger",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                        }

                        if (selectedTab == 0) {
                            FloatingActionButton(
                                onClick = { isAddDocDialogOpen = true },
                                containerColor = CyberCyan,
                                contentColor = Color.Black,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Document", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberChip(
                            label = "RAG Documents (${sampleDocs.size})",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        CyberChip(
                            label = "Working Memory Ledger (${persistentMemories.size})",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // Tab Content
            if (selectedTab == 0) {
                val filteredDocs = remember(sampleDocs.toList(), searchQuery) {
                    if (searchQuery.isBlank()) sampleDocs
                    else sampleDocs.filter {
                        it.title.contains(searchQuery, ignoreCase = true) || it.status.contains(searchQuery, ignoreCase = true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search indexed chunks...", color = ThinkingMutedSlate, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xCC0E1322),
                            unfocusedContainerColor = Color(0xCC0E1322),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (filteredDocs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = ThinkingMutedSlate, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No matching documents found", style = MaterialTheme.typography.bodyMedium, color = ThinkingMutedSlate)
                            }
                        }
                    } else {
                        // Documents List
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = filteredDocs,
                                key = { it.id },
                                contentType = { "rag_doc_card" }
                            ) { doc ->
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = if (doc.isIndexed) CyberCyan.copy(alpha = 0.3f) else AmberAccent.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    tintColor = Color(0xCC0E1322),
                                    onClick = {
                                        if (!doc.isIndexed) {
                                            // Finish processing immediately on click
                                            val index = sampleDocs.indexOfFirst { it.id == doc.id }
                                            if (index >= 0) {
                                                sampleDocs[index] = doc.copy(status = "Indexed", isIndexed = true)
                                            }
                                        } else {
                                            selectedDocForDetails = doc
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(14.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = if (doc.isIndexed) CyberCyan else AmberAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = doc.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${doc.chunkCount} vector embeddings • 384-dim",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = ThinkingMutedSlate
                                            )
                                        }
                                        NeonStatusBadge(
                                            text = doc.status,
                                            color = if (doc.isIndexed) StatusSuccess else StatusWarning,
                                            isPulsing = !doc.isIndexed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                MemoryInspectorView(
                    memories = persistentMemories,
                    contextTokensUsed = 1420,
                    contextLimit = 4096,
                    kvCacheMb = 192,
                    onAddMemory = { content, tags ->
                        persistentMemories.add(0, MemoryEntity(content = content, tags = tags))
                    },
                    onDeleteMemory = { id ->
                        persistentMemories.removeAll { it.id == id }
                    },
                    onClearAll = {
                        persistentMemories.clear()
                    }
                )
            }
        }
    }
}
