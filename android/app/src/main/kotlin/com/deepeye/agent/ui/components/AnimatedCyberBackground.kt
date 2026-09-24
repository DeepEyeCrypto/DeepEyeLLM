package com.deepeye.agent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.random.Random

private class CyberParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val baseAlpha: Float
)

/**
 * 60fps GPU-Accelerated Dynamic Animated Neural Background.
 * Features drifting constellation nodes, interactive proximity links,
 * and pulsating ambient glassmorphic color flares.
 */
@Composable
fun AnimatedCyberBackground(
    modifier: Modifier = Modifier,
    particleCount: Int = 36
) {
    // 1. Ambient Pulsing Radial Flares
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_pulse")

    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse3"
    )

    // 2. Dynamic Physics-based Particles
    val particles = remember {
        val rand = Random(42)
        List(particleCount) {
            CyberParticle(
                x = rand.nextFloat() * 1080f,
                y = rand.nextFloat() * 2400f,
                vx = (rand.nextFloat() - 0.5f) * 0.75f,
                vy = (rand.nextFloat() - 0.5f) * 0.75f,
                radius = rand.nextFloat() * 2.5f + 1.5f,
                baseAlpha = rand.nextFloat() * 0.4f + 0.35f
            )
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (isActive) {
            withFrameNanos { time ->
                if (lastTime != 0L) {
                    val dt = ((time - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    for (p in particles) {
                        p.x += p.vx * dt * 60f
                        p.y += p.vy * dt * 60f

                        // Wrap edges smoothly
                        if (p.x < -20f) p.x = 1100f
                        if (p.x > 1100f) p.x = -20f
                        if (p.y < -20f) p.y = 2420f
                        if (p.y > 2420f) p.y = -20f
                    }
                    frameTick = time
                }
                lastTime = time
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060911))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // ── Draw Ambient Glowing Flares ───────────────────────────────────
            // Top Left Cyan Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.22f * pulse1),
                        Color(0xFF00E5FF).copy(alpha = 0.08f * pulse1),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.15f, height * 0.18f),
                    radius = width * 0.75f * pulse1
                ),
                radius = width * 0.75f * pulse1,
                center = Offset(width * 0.15f, height * 0.18f)
            )

            // Center Right Deep Indigo Orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7928CA).copy(alpha = 0.20f * pulse2),
                        Color(0xFF7928CA).copy(alpha = 0.06f * pulse2),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f, height * 0.48f),
                    radius = width * 0.85f * pulse2
                ),
                radius = width * 0.85f * pulse2,
                center = Offset(width * 0.85f, height * 0.48f)
            )

            // Bottom Left Electric Teal Flare
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00F2FE).copy(alpha = 0.18f * pulse3),
                        Color(0xFF00F2FE).copy(alpha = 0.05f * pulse3),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.3f, height * 0.82f),
                    radius = width * 0.7f * pulse3
                ),
                radius = width * 0.7f * pulse3,
                center = Offset(width * 0.3f, height * 0.82f)
            )

            // ── Draw Constellation Links & Nodes ──────────────────────────────
            // Trigger read for frame tick
            val currentFrame = frameTick
            val maxLinkDistance = width * 0.32f

            val scaleX = width / 1080f
            val scaleY = height / 2400f

            for (i in particles.indices) {
                val p1 = particles[i]
                val p1X = p1.x * scaleX
                val p1Y = p1.y * scaleY

                for (j in i + 1 until particles.size) {
                    val p2 = particles[j]
                    val p2X = p2.x * scaleX
                    val p2Y = p2.y * scaleY

                    val dist = hypot(p2X - p1X, p2Y - p1Y)
                    if (dist < maxLinkDistance) {
                        val linkAlpha = (1f - (dist / maxLinkDistance)) * 0.28f
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = linkAlpha),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 1.2f
                        )
                    }
                }
            }

            // Draw glowing nodes
            for (p in particles) {
                val px = p.x * scaleX
                val py = p.y * scaleY

                // Outer soft halo
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = p.baseAlpha * 0.3f),
                    radius = p.radius * 3.2f,
                    center = Offset(px, py)
                )

                // Core crisp white/cyan dot
                drawCircle(
                    color = Color(0xFFE0F7FA).copy(alpha = p.baseAlpha),
                    radius = p.radius,
                    center = Offset(px, py)
                )
            }

            // ── Vignette for Text Legibility ──────────────────────────────────
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD9060911),
                        Color.Transparent,
                        Color.Transparent,
                        Color(0xF0060911)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }
    }
}
