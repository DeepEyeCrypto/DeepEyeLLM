package com.deepeye.agent.ui.p2p

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.CyberButton
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.components.RadarDiscoveryCanvas
import com.deepeye.agent.ui.components.RadarPeer
import com.deepeye.agent.ui.theme.*

data class NearbyDeviceUi(
    val id: String,
    val name: String,
    val signal: String,
    val isVerified: Boolean
)

@Composable
fun P2PShareScreen() {
    val radarPeers = remember {
        listOf(
            RadarPeer("1", "DeepEye Pixel 8 Pro", distanceRatio = 0.45f, angleDegrees = 45f, isVerified = true, rssi = "-45 dBm"),
            RadarPeer("2", "Workstation Node (Nearby)", distanceRatio = 0.75f, angleDegrees = 210f, isVerified = true, rssi = "-62 dBm")
        )
    }

    var isTransferring by remember { mutableStateOf(false) }
    var transferProgress by remember { mutableFloatStateOf(0.68f) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Air-Gapped P2P Mesh",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Encrypted Local BLE + Wi-Fi Direct Sharding",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }

                    NeonStatusBadge(
                        text = "Mesh Online",
                        isPulsing = true,
                        color = StatusSuccess
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // Radar Canvas Card
            item {
                RadarDiscoveryCanvas(peers = radarPeers)
            }

            // Active Shard Transfer Card
            item {
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("gemma-2-2b-it-q4_k_m.gguf", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Chunk 1,420 of 2,048 • 45.2 MB/s", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = ThinkingMutedSlate)
                                }
                            }

                            Text(
                                text = "${(transferProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = StatusSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { transferProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StatusSuccess,
                            trackColor = Color(0x3300E676)
                        )
                    }
                }
            }

            // Discovered Nodes Header
            item {
                Text(
                    text = "DISCOVERED NODES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ThinkingMutedSlate
                )
            }

            items(radarPeers, key = { it.id }) { peer ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    tintColor = Color(0xCC0E1322)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = peer.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "Signal: ${peer.rssi} • Verified Ed25519 Key",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = ThinkingMutedSlate
                                )
                            }
                        }

                        CyberButton(
                            onClick = { isTransferring = true },
                            accentColor = CyberCyan
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Shard", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
