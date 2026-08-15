package com.deepeye.agent.core.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkillRegistryTest {

    private lateinit var registry: SkillRegistry

    @Before
    fun setUp() {
        registry = SkillRegistry()
    }

    @Test
    fun builtinSkills_containStandardManifests() {
        val skills = SkillRegistry.BUILTIN_SKILLS
        assertTrue(skills.isNotEmpty())

        val cryptoSentinel = skills.find { it.id == "crypto-sentinel" }
        assertNotNull(cryptoSentinel)
        assertEquals("Crypto & DeFi", cryptoSentinel!!.category)
        assertTrue(cryptoSentinel.toolsProvided.contains("dex_screener"))
        assertTrue(cryptoSentinel.verificationGates.isNotEmpty())
        assertTrue(cryptoSentinel.antiRationalizationRules.isNotEmpty())
    }

    @Test
    fun markInstalled_updatesSkillState() {
        val skillId = "code-auditor"
        registry.markInstalled(skillId)

        val skill = registry.getSkill(skillId)
        assertNotNull(skill)
        assertTrue(skill!!.isInstalled)
    }

    @Test
    fun clear_resetsInstallationState() {
        registry.markInstalled("crypto-sentinel")
        registry.clear()

        val skill = registry.getSkill("crypto-sentinel")
        assertNotNull(skill)
        assertTrue(!skill!!.isInstalled)
    }
}
