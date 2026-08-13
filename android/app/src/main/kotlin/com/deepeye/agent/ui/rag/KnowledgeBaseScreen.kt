package com.deepeye.agent.ui.rag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.DeepEyeTheme

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
    var searchQuery by remember { mutableStateOf("") }
    
    val sampleDocs = remember {
        listOf(
            RagDocument("1", "Architecture_Decisions.md", 42, "Indexed", true),
            RagDocument("2", "DeepEye_Security_Model.pdf", 128, "Indexed", true),
            RagDocument("3", "Operations_Runbook.txt", 19, "Processing...", false)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Knowledge Base (RAG)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% On-Device Document Vector Index",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepEyeTheme.colors.link
                    )
                }

                FloatingActionButton(
                    onClick = { /* Import Doc */ },
                    containerColor = DeepEyeTheme.colors.link,
                    contentColor = Color(0xFF0B0F19),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Document")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search indexed chunks...", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DeepEyeTheme.colors.link) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepEyeTheme.colors.link,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF151A29).copy(alpha = 0.6f),
                    unfocusedContainerColor = Color(0xFF151A29).copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Documents List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sampleDocs) { doc ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (doc.isIndexed) DeepEyeTheme.colors.link.copy(alpha = 0.3f) else Color(0xFFFFC107).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        tintColor = Color(0xFF151A29).copy(alpha = 0.75f),
                        borderColor = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = DeepEyeTheme.colors.link,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${doc.chunkCount} vector chunks • Embedded",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            NeonStatusBadge(
                                text = doc.status,
                                isPulsing = !doc.isIndexed,
                                color = if (doc.isIndexed) DeepEyeTheme.colors.statusSuccess else Color(0xFFFFC107)
                            )
                        }
                    }
                }
            }
        }
    }
}
