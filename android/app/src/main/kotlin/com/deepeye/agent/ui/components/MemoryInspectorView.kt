package com.deepeye.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.core.memory.MemoryEntity
import com.deepeye.agent.ui.theme.*

/**
 * Transparent Memory Inspector and Working Context Ledger Component.
 * Enables users to inspect, edit, pin, and purge agent memories with full visibility.
 */
@Composable
fun MemoryInspectorView(
    memories: List<MemoryEntity>,
    modifier: Modifier = Modifier,
    contextTokensUsed: Int = 1240,
    contextLimit: Int = 4096,
    kvCacheMb: Int = 180,
    onAddMemory: (content: String, tags: String) -> Unit,
    onDeleteMemory: (id: String) -> Unit,
    onClearAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    if (isAddDialogOpen) {
        var contentInput by remember { mutableStateOf("") }
        var tagsInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { isAddDialogOpen = false },
            title = { Text("Store Persistent Memory", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Add a sovereign persistent memory rule or fact to Hermes on-device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThinkingMutedSlate
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = { contentInput = it },
                        label = { Text("Fact / System Invariant") },
                        placeholder = { Text("e.g. Always verify LP lock on Uniswap pools") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("Tags (comma separated)") },
                        placeholder = { Text("crypto, security, user_pref") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contentInput.isNotBlank()) {
                            onAddMemory(contentInput.trim(), tagsInput.trim())
                            isAddDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                ) {
                    Text("Save Memory", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddDialogOpen = false }) {
                    Text("Cancel", color = ThinkingMutedSlate)
                }
            },
            containerColor = Color(0xF20E1322)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Working Context Ledger Card
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, TelemetryBorder), shape = RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            tintColor = Color(0xCC0E1322)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "WORKING CONTEXT LEDGER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }

                    Text(
                        text = "$contextTokensUsed / $contextLimit tokens",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CyberCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val contextRatio = (contextTokensUsed.toFloat() / contextLimit.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { contextRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        contextRatio > 0.85f -> StatusError
                        contextRatio > 0.65f -> AmberAccent
                        else -> CyberCyan
                    },
                    trackColor = Color(0x3300E5FF)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "KV-Cache: ${kvCacheMb}MB allocated",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = ThinkingMutedSlate
                    )
                    Text(
                        text = "${((1f - contextRatio) * 100).toInt()}% headroom",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = StatusSuccess
                    )
                }
            }
        }

        // Episodic Memory Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Episodic Knowledge Store",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${memories.size} persistent memory slots stored locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = ThinkingMutedSlate
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { isAddDialogOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = CyberCyan, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StatusError.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Memories", tint = StatusError, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search stored facts or tags...", color = ThinkingMutedSlate, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color(0xCC0E1322),
                unfocusedContainerColor = Color(0xCC0E1322),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Memories List
        val filteredMemories = remember(memories, searchQuery) {
            if (searchQuery.isBlank()) memories
            else memories.filter {
                it.content.contains(searchQuery, ignoreCase = true) || it.tags.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = ThinkingMutedSlate, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No memory slots found", style = MaterialTheme.typography.bodyMedium, color = ThinkingMutedSlate)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { memory ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xB30E1322),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memory.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                                if (memory.tags.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        memory.tags.split(",").forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x33B388FF))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "#${tag.trim()}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = PolicyPurpleDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onDeleteMemory(memory.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Memory",
                                    tint = ThinkingMutedSlate,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
