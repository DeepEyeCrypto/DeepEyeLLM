package com.deepeye.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the PromptTemplate framework (src/main/java/.../core/PromptTemplate.kt).
 * All functions under test are pure — no native/Colibri dependency.
 */
class PromptTemplateTest {

    @Test
    fun scorePromptStaysInRange0to10() {
        val prompt = PromptTemplate.buildClassification("hi there", listOf("bug", "feature"))
        val score = PromptTemplate.scorePrompt(prompt)
        assertTrue("score $score out of range", score in 0.0..10.0)
    }

    @Test
    fun builtInClassificationPromptHasNoUnresolvedPlaceholders() {
        val prompt = PromptTemplate.buildClassification(
            "this is a support ticket about a crash",
            listOf("bug", "feature", "question")
        )
        assertTrue(prompt.contains("bug, feature, question"))
        assertTrue(prompt.contains("this is a support ticket about a crash"))
        assertFalse("rendered prompt still contains {{var}}", prompt.contains("{{"))
    }

    @Test
    fun renderWithMissingVarsReportsThem() {
        val result = PromptTemplate.render(PromptTemplate.Patterns.CLASSIFICATION, emptyMap())
        assertFalse(result.isValid)
        assertTrue(result.missingVariables.contains("input"))
        assertTrue(result.missingVariables.contains("labels"))
    }

    @Test
    fun renderWithAllVarsIsValid() {
        val result = PromptTemplate.render(
            PromptTemplate.Patterns.CLASSIFICATION,
            mapOf("input" to "text", "labels" to "a, b")
        )
        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.missingVariables)
    }

    @Test
    fun substituteReplacesPlaceholdersAndReportsMissing() {
        val (out, missing) = PromptTemplate.substitute("A {{x}} B {{y}} C", mapOf("x" to "1"))
        assertEquals("A 1 B  C", out)
        assertEquals(listOf("y"), missing)
    }

    @Test
    fun extractionBuilderProducesJsonInstruction() {
        val prompt = PromptTemplate.buildExtraction("invoice body", listOf("date", "amount"))
        assertTrue(prompt.contains("date, amount"))
        assertTrue(prompt.lowercase().contains("json"))
    }

    @Test
    fun qaBuilderRoutesEmptyContextToOpenDomain() {
        val open = PromptTemplate.buildQA("What is Kotlin?")
        assertTrue(open.contains("What is Kotlin?"))
        val grounded = PromptTemplate.buildQA("What is Kotlin?", "Kotlin is a language.")
        assertTrue(grounded.contains("Kotlin is a language."))
        assertTrue(grounded.contains("insufficient context"))
    }

    @Test
    fun customPatternCanBeRegisteredAtRuntime() {
        PromptTemplate.register(
            PromptTemplate.TaskPattern(
                id = "translate-test",
                taskType = "translation",
                description = "translate text",
                template = "Translate to {{targetLanguage}}:\n{{input}}"
            )
        )
        val result = PromptTemplate.render(
            "translate-test",
            mapOf("targetLanguage" to "Spanish", "input" to "hello")
        )
        assertTrue(result.isValid)
        assertTrue(result.prompt.contains("Translate to Spanish:"))
    }
}
