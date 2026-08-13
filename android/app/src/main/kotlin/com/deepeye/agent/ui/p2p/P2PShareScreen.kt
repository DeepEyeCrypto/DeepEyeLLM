package com.deepeye.agent.ui.p2p

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.components.GlassCard
import com.deepeye.agent.ui.components.NeonStatusBadge
import com.deepeye.agent.ui.theme.DeepEyeTheme

data class NearbyDeviceUi(
    val id: String,
    val name: String,
    val signal: String,
    val isVerified: Boolean
)

@Composable
fun P2PShareScreen() {
    val devices = remember {
        listOf(
            NearbyDeviceUi("1", "DeepEye Pixel 8 Pro", "-45 dBm", true),
            NearbyDeviceUi("2", "OnePlus 12 (Nearby)", "-62 dBm", false)
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
                        text = "P2P Model Sharing",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device-to-Device Encrypted Transfer",
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepEyeTheme.colors.link
                    )
                }

                NeonStatusBadge(
                    text = "Radar Active",
                    isPulsing = true,
                    color = DeepEyeTheme.colors.link
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices) { dev ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = DeepEyeTheme.colors.link.copy(alpha = 0.25f),
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
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = DeepEyeTheme.colors.link,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Signal: ${dev.signal} • Verified TLS",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = { /* Share model */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeepEyeTheme.colors.link.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
