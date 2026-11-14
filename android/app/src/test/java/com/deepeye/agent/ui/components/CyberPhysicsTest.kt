package com.deepeye.agent.ui.components

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the extracted CyberPhysics (CyberPhysics.kt).
 * Pure JVM — no Android/Compose dependency.
 */
class CyberPhysicsTest {

    @Test
    fun stepMovesParticlesByVelocityTimesDt() {
        val p = CyberParticle(x = 100f, y = 100f, vx = 10f, vy = 0f, radius = 2f, baseAlpha = 0.5f)
        p.step(dt = 0.016f, boundsW = 1080f, boundsH = 2400f)
        // x += vx * dt * 60 = 10 * 0.016 * 60 = 9.6
        assertEquals(109.6f, p.x, 0.1f)
        assertEquals(100f, p.y, 0.001f)
    }

    @Test
    fun particleWrapsAcrossEdges() {
        val p = CyberParticle(x = 1095f, y = 100f, vx = 10f, vy = 0f, radius = 2f, baseAlpha = 0.5f)
        p.step(dt = 1f, boundsW = 1080f, boundsH = 2400f) // x -> 1095+600=1695 > 1100 -> wrap to -20
        assertTrue("expected wrap to left edge, got x=${p.x}", p.x <= 0f)
        assertEquals(-20f, p.x, 0.001f)
    }

    @Test
    fun linkPairsFindsOnlyClosePairs() {
        val a = CyberParticle(0f, 0f, 0f, 0f, 2f, 0.5f)
        val b = CyberParticle(30f, 40f, 0f, 0f, 2f, 0.5f) // dist=50 -> linkable at maxDist=60
        val c = CyberParticle(300f, 0f, 0f, 0f, 2f, 0.5f)  // dist=300 -> not linkable
        val links = linkPairs(listOf(a, b, c), maxDist = 60f, scale = 1f)
        assertEquals(listOf(0 to 1), links)
    }

    @Test
    fun cappedCountClampsToCap() {
        assertEquals(24, cappedCount(desired = 38, cap = 24))
        assertEquals(12, cappedCount(desired = 12, cap = 24))
    }

    @Test
    fun defaultParticlesRespectsCapAndSeed() {
        val list = defaultParticles(count = 38)
        assertEquals(24, list.size)
        // deterministic seed -> same first particle twice
        val again = defaultParticles(count = 38)
        assertEquals(list.first().x, again.first().x, 0.0001f)
        assertEquals(list.first().y, again.first().y, 0.0001f)
    }
}
