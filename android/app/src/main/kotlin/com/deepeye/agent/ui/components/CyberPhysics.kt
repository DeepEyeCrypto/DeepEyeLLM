package com.deepeye.agent.ui.components

import kotlin.random.Random

/**
 * Pure (JVM-testable) particle physics for the cyber background.
 * Extracted from AnimatedCyberBackground so the O(n²) link pass and
 * per-frame stepping can be unit-tested without Compose.
 */
class CyberParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val baseAlpha: Float
) {
    /** Advance one frame. [dt] in seconds, [boundsW]/[boundsH] in design space (1080x2400). */
    fun step(dt: Float, boundsW: Float, boundsH: Float) {
        x += vx * dt * 60f
        y += vy * dt * 60f

        // Wrap edges smoothly
        if (x < -20f) x = boundsW + 20f
        if (x > boundsW + 20f) x = -20f
        if (y < -20f) y = boundsH + 20f
        if (y > boundsH + 20f) y = -20f
    }
}

/**
 * Pairs (i, j) with i < j whose distance is under [maxDist] (scaled by [scale]).
 * Uses squared distance to avoid hypot() for the hot pre-filter; the caller
 * may sqrt() only for the small number of pairs that actually link.
 */
fun linkPairs(particles: List<CyberParticle>, maxDist: Float, scale: Float = 1f): List<Pair<Int, Int>> {
    val maxSq = (maxDist * scale) * (maxDist * scale)
    val out = ArrayList<Pair<Int, Int>>()
    for (i in particles.indices) {
        val a = particles[i]
        for (j in i + 1 until particles.size) {
            val b = particles[j]
            val dx = (b.x - a.x) * scale
            val dy = (b.y - a.y) * scale
            if (dx * dx + dy * dy < maxSq) out.add(i to j)
        }
    }
    return out
}

/** Cap the desired particle count at [cap] — hard ceiling for the render cost. */
fun cappedCount(desired: Int, cap: Int): Int = minOf(desired, cap)

/** Deterministic default particle set (seeded so layout is stable across launches). */
fun defaultParticles(count: Int, rand: Random = Random(42)): List<CyberParticle> =
    List(cappedCount(count, 24)) {
        CyberParticle(
            x = rand.nextFloat() * 1080f,
            y = rand.nextFloat() * 2400f,
            vx = (rand.nextFloat() - 0.5f) * 0.75f,
            vy = (rand.nextFloat() - 0.5f) * 0.75f,
            radius = rand.nextFloat() * 2.5f + 1.5f,
            baseAlpha = rand.nextFloat() * 0.4f + 0.35f
        )
    }
