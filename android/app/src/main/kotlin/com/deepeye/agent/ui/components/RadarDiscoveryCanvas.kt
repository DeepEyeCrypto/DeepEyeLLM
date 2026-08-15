package com.deepeye.agent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepeye.agent.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

data class RadarPeer(
    val id: String,
    val name: String,
    val distanceRatio: Float, // 0.0 to 1.0 from center
    val angleDegrees: Float,  // 0 to 360
    val isVerified: Boolean,
    val rssi: String
)

/**
 * Tactical Radar Discovery Sweep Canvas for P2P Model & Knowledge Mesh.
 * Displays real-time nearby cryptographic peer nodes with zero cloud dependencies.
 */
@Composable
fun RadarDiscoveryCanvas(
    peers: List<RadarPeer>,
    modifier: Modifier = Modifier,
    isScanning: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0E1322),
        border = androidx.compose.foundation.BorderStroke(1.dp, TelemetryBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width / 2

                    // Concentric Radar Range Rings
                    for (i in 1..4) {
                        val radius = maxRadius * (i / 4f)
                        drawCircle(
                            color = Color(0x2600E5FF),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Crosshair Lines
                    drawLine(
                        color = Color(0x2600E5FF),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0x2600E5FF),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Rotating Radar Sweep Line
                    if (isScanning) {
                        val sweepRad = Math.toRadians(sweepAngle.toDouble())
                        val endX = center.x + (maxRadius * cos(sweepRad)).toFloat()
                        val endY = center.y + (maxRadius * sin(sweepRad)).toFloat()

                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, CyberCyan),
                                start = center,
                                end = Offset(endX, endY)
                            ),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    // Discovered Peer Blips
                    peers.forEach { peer ->
                        val peerRad = Math.toRadians(peer.angleDegrees.toDouble())
                        val peerDist = maxRadius * peer.distanceRatio.coerceIn(0.2f, 0.9f)
                        val peerX = center.x + (peerDist * cos(peerRad)).toFloat()
                        val peerY = center.y + (peerDist * sin(peerRad)).toFloat()

                        val blipColor = if (peer.isVerified) StatusSuccess else AmberAccent

                        // Blip Outer Glow
                        drawCircle(
                            color = blipColor.copy(alpha = 0.3f),
                            radius = 8.dp.toPx(),
                            center = Offset(peerX, peerY)
                        )
                        // Blip Core
                        drawCircle(
                            color = blipColor,
                            radius = 4.dp.toPx(),
                            center = Offset(peerX, peerY)
                        )
                    }
                }
            }

            // Radar Status Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Air-Gapped Discovery: 2 Peers",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = CyberCyan
                )
                Text(
                    text = "BLE + Wi-Fi Direct Mesh",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = ThinkingMutedSlate
                )
            }
        }
    }
}
