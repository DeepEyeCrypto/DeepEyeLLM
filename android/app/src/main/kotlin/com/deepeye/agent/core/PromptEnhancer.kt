package com.deepeye.agent.core

import android.util.Log

/**
 * Prompt enhancement utilities for DeepEyeLLM.
 * Provides prompt improvement, formatting, and optimization functions
 * that can be used with the Colibri engine and other LLM integrations.
 *
 * Currently supports:
 * - Prompt formatting and structuring
 * - Few-shot example injection
 * - Task-focused prompt refinement
 * - Token-efficient prompt trimming
 */
object PromptEnhancer {

    private val TAG = "PromptEnhancer"

    /** Format a prompt with clear task description and constraints.
     *  Improves LLM performance by making the task explicit.
     */
    fun formatTaskPrompt(
        task: String,
        context: String? = null,
        constraints: String? = null,
        fewShots: List<String>? = null
    ): String {
        val sb = StringBuilder()

        // Task description
        sb.append("Task: $task\n")

        // Context
        context?.let { sb.append("Context: $it\n\n") }

        // Constraints
        constraints?.let { sb.append("Constraints: $it\n\n") }

        // Few-shot examples
        fewShots?.forEach { shot ->
            sb.append("Example: $shot\n")
        }

        // Default instruction
        sb.append("Instructions: Provide a clear, concise, and accurate response.\n")

        return sb.toString()
    }

    /** Improve a prompt by adding structure and clarity.
     *  Uses pattern-based enhancement for common LLM tasks.
     */
    fun improvePrompt(
        rawPrompt: String,
        taskType: TaskType = TaskType.GENERAL
    ): String {
        val base = rawPrompt.trim()

        return when (taskType) {
            TaskType.CLASSIFICATION -> improveClassificationPrompt(base)
            TaskType.SUMMARIZATION -> improveSummarizationPrompt(base)
            TaskType.CODE_REVIEW -> improveCodeReviewPrompt(base)
            TaskType.CREATIVE -> improveCreativePrompt(base)
            TaskType.ANALYSIS -> improveAnalysisPrompt(base)
            else -> improveGeneralPrompt(base)
        }
    }

    /** Classification-task prompt improvement. */
    private fun improveClassificationPrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Classify the following text into exactly one category:\n$prompt".trim()
    }

    /** Summarization-task prompt improvement. */
    private fun improveSummarizationPrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Summarize the key points from the following text in 3-5 bullet points:\n$prompt".trim()
    }

    /** Code review-task prompt improvement. */
    private fun improveCodeReviewPrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Review the following code for bugs, security issues, and code quality improvements:\n$prompt".trim()
    }

    /** Creative-task prompt improvement. */
    private fun improveCreativePrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Write a creative piece based on the following prompt. Be original and engaging:\n$prompt".trim()
    }

    /** Analysis-task prompt improvement. */
    private fun improveAnalysisPrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Analyze the following text and identify key themes, patterns, and insights:\n$prompt".trim()
    }

    /** General-task prompt improvement. */
    private fun improveGeneralPrompt(prompt: String): String {
        if (prompt.length < 20) return prompt
        return "Please analyze and respond to the following request:\n$prompt".trim()
    }

    /** Trim a prompt to token-efficient length while preserving meaning.
     *  Useful for models with context token limits.
     */
    fun trimPrompt(
        prompt: String,
        maxTokens: Int = 2048,
        modelName: String = "colibri"
    ): String {
        // Simple token approximation: ~4 chars per token for English
        val approxChars = maxTokens * 4
        if (prompt.length <= approxChars) return prompt

        // Truncate at sentence boundary
        val truncated = prompt.substring(0, approxChars - 10)
        val lastPeriod = truncated.lastIndexOf(". ")
        if (lastPeriod > 0) {
            return truncated.substring(0, lastPeriod + 1)
        }
        return truncated + "..."
    }

    /** Add few-shot examples to a prompt for better LLM performance.
     *  @param examples  List of "input -> output" pairs
     *  @param separator Separator between examples
     */
    fun addFewShots(
        prompt: String,
        examples: List<String>,
        separator: String = "\n\n"
    ): String {
        val sb = StringBuilder(prompt)
        if (prompt.endsWith("\n")) sb.delete(sb.length - 1, sb.length)
        examples.forEach { example ->
            sb.append(separator).append(example)
        }
        sb.append("\n")
        return sb.toString()
    }

    /** Data class for task types. */
    enum class TaskType {
        GENERAL,
        CLASSIFICATION,
        SUMMARIZATION,
        CODE_REVIEW,
        CREATIVE,
        ANALYSIS
    }
}