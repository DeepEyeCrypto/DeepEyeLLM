package com.deepeye.agent.core

/**
 * PromptTemplate — extensible prompt-template framework for LLM prompt enhancement.
 *
 * Provides:
 *  - Built-in task patterns: classification, summarization, extraction, question answering.
 *  - `{{variable}}` placeholder substitution via [substitute] / [render].
 *  - An open registry: custom [TaskPattern]s can be added at runtime with [register],
 *    so new task types do not require touching this file.
 *  - APO-inspired quality scoring of rendered prompts before Colibri inference ([scorePrompt]).
 *
 * Usage:
 * ```
 * // Built-in patterns
 * val prompt = PromptTemplate.render(
 *     PromptTemplate.Patterns.CLASSIFICATION,
 *     mapOf("input" to "...", "labels" to "bug, feature, question")
 * ).prompt
 *
 * // Convenience builders
 * val jsonPrompt = PromptTemplate.buildExtraction(input, listOf("date", "amount", "currency"))
 *
 * // Extensibility
 * PromptTemplate.register(
 *     PromptTemplate.TaskPattern(
 *         id = "translate",
 *         taskType = "translation",
 *         description = "Translate text to a target language",
 *         template = "Translate the following to {{targetLanguage}}:\n{{input}}"
 *     )
 * )
 * val translated = PromptTemplate.render("translate", mapOf("targetLanguage" to "Spanish", "input" to "hello"))
 * ```
 */
object PromptTemplate {

    // ---------------------------------------------------------------------------
    // Regex + substitution
    // ---------------------------------------------------------------------------

    /** Matches `{{varName}}` placeholders (whitespace-tolerant, dots/underscores allowed). */
    private val VAR_REGEX = Regex("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}")

    /**
     * Substitute `{{var}}` placeholders in [template] using [vars].
     * Missing variables are replaced with an empty string and reported,
     * so the caller can fail fast before spending inference budget.
     *
     * @return the substituted text and the list of missing variable names (in template order).
     */
    fun substitute(template: String, vars: Map<String, String>): Pair<String, List<String>> {
        val missing = LinkedHashSet<String>()
        val out = VAR_REGEX.replace(template) { m ->
            val name = m.groupValues[1]
            val value = vars[name]
            if (value == null) {
                missing.add(name)
                ""
            } else value
        }
        return out to missing.toList()
    }

    // ---------------------------------------------------------------------------
    // Result
    // ---------------------------------------------------------------------------

    /** Outcome of rendering a [TaskPattern] with concrete variables. */
    data class RenderResult(
        val prompt: String,
        val missingVariables: List<String>,
        val score: Double
    ) {
        /** True when every placeholder had a supplied value. */
        val isValid: Boolean get() = missingVariables.isEmpty()
    }

    // ---------------------------------------------------------------------------
    // TaskPattern + Registry (extensible framework)
    // ---------------------------------------------------------------------------

    /**
     * A named, pluggable prompt pattern.
     *
     * [template] uses `{{var}}` placeholders. [variables] is derived from the
     * template at construction time so missing inputs can be detected pre-inference.
     *
     * @param id        Unique registry key (e.g. "classification").
     * @param taskType  Coarse task family (e.g. "extraction") for routing/UI grouping.
     * @param description Human-readable summary (used in Prompt Lab lists).
     * @param template  Prompt body with `{{var}}` placeholders.
     */
    data class TaskPattern(
        val id: String,
        val taskType: String,
        val description: String,
        val template: String
    ) {
        /** Placeholder names found in [template], in order of first appearance. */
        val variables: List<String> = Companion.extractVariables(template)

        companion object {
            /** Extract `{{var}}` names from a template string. */
            fun extractVariables(template: String): List<String> =
                VAR_REGEX.findAll(template).map { it.groupValues[1] }.distinct().toList()
        }
    }

    // ---------------------------------------------------------------------------
    // Built-in task patterns
    // ---------------------------------------------------------------------------

    object Patterns {

        /** Assign a label from a fixed set of categories. */
        val CLASSIFICATION = TaskPattern(
            id = "classification",
            taskType = "classification",
            description = "Assign a label to input text from a fixed set of categories",
            template =
                """
                Classify the following text into exactly one of these categories: {{labels}}.

                Rules:
                - Respond with only the category name and nothing else.
                - If the text fits none of the categories, respond with "unknown".

                Text:
                {{input}}
                """.trimIndent()
        )

        /** Condense long text into a bounded summary. */
        val SUMMARIZATION = TaskPattern(
            id = "summarization",
            taskType = "summarization",
            description = "Condense long text into a concise, bounded summary",
            template =
                """
                Summarize the following text in no more than {{maxLength}} words.
                Focus areas: {{focusAreas}}

                Rules:
                - Preserve key facts, names, and numbers.
                - Do not add information that is not present in the text.

                Text:
                {{input}}
                """.trimIndent()
        )

        /** Extract structured fields from text as JSON. */
        val EXTRACTION = TaskPattern(
            id = "extraction",
            taskType = "extraction",
            description = "Extract structured fields from text, returned as JSON",
            template =
                """
                Extract the following fields from the text: {{fields}}.

                Rules:
                - Respond with a single valid JSON object and nothing else.
                - Use the exact keys listed above.
                - Set a key to null if its value cannot be found in the text.

                Text:
                {{input}}

                JSON:
                """.trimIndent()
        )

        /** Grounded QA: answer strictly from the supplied context. */
        val QUESTION_ANSWERING = TaskPattern(
            id = "qa",
            taskType = "question_answering",
            description = "Answer a question grounded strictly in the provided context",
            template =
                """
                Answer the question using only the context below.
                If the context does not contain the answer, respond with "insufficient context".

                Question: {{question}}

                Context:
                {{context}}
                """.trimIndent()
        )

        /** Open QA: answer from model knowledge, no context. */
        val QUESTION_ANSWERING_OPEN = TaskPattern(
            id = "qa_open",
            taskType = "question_answering",
            description = "Answer a question using general knowledge",
            template =
                """
                Answer the question as accurately and concisely as possible.
                If you are not sure, say so briefly instead of guessing.

                Question: {{question}}
                """.trimIndent()
        )
    }

    // ---------------------------------------------------------------------------
    // Registry
    // ---------------------------------------------------------------------------

    private val registry = LinkedHashMap<String, TaskPattern>()

    init {
        register(Patterns.CLASSIFICATION)
        register(Patterns.SUMMARIZATION)
        register(Patterns.EXTRACTION)
        register(Patterns.QUESTION_ANSWERING)
        register(Patterns.QUESTION_ANSWERING_OPEN)
    }

    /** Register or override a task pattern. Returns the registered [pattern]. */
    fun register(pattern: TaskPattern): TaskPattern {
        require(pattern.id.isNotBlank()) { "TaskPattern.id must not be blank" }
        registry[pattern.id] = pattern
        return pattern
    }

    /** Look up a registered pattern by [id]. */
    fun pattern(id: String): TaskPattern =
        registry[id] ?: throw IllegalArgumentException(
            "Unknown task pattern '$id'. Registered: ${registry.keys}"
        )

    /** All registered patterns (for listing in UIs such as Prompt Lab). */
    fun patterns(): List<TaskPattern> = registry.values.toList()

    // ---------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------

    /** Render a registered pattern by [id] with [vars]. */
    fun render(id: String, vars: Map<String, String> = emptyMap()): RenderResult =
        render(pattern(id), vars)

    /**
     * Substitute [vars] into [pattern]'s template and score the result.
     * Missing placeholders are reported in [RenderResult.missingVariables].
     */
    fun render(pattern: TaskPattern, vars: Map<String, String> = emptyMap()): RenderResult {
        val (substituted, missing) = substitute(pattern.template, vars)
        return RenderResult(substituted, missing, scorePrompt(substituted))
    }

    // ---------------------------------------------------------------------------
    // Convenience builders (one-liners over the registry)
    // ---------------------------------------------------------------------------

    /** Classification prompt: [input] text, [labels] category set. */
    fun buildClassification(input: String, labels: List<String>): String =
        render(
            Patterns.CLASSIFICATION,
            mapOf("input" to input, "labels" to labels.joinToString(", "))
        ).prompt

    /** Summarization prompt: [input] text, [maxLength] word budget, [focusAreas] guidance. */
    fun buildSummarization(
        input: String,
        maxLength: Int = 120,
        focusAreas: String = "the most important facts"
    ): String =
        render(
            Patterns.SUMMARIZATION,
            mapOf(
                "input" to input,
                "maxLength" to maxLength.toString(),
                "focusAreas" to focusAreas
            )
        ).prompt

    /** Extraction prompt: [input] text, [fields] JSON keys to pull out. */
    fun buildExtraction(input: String, fields: List<String>): String =
        render(
            Patterns.EXTRACTION,
            mapOf("input" to input, "fields" to fields.joinToString(", "))
        ).prompt

    /**
     * QA prompt: [question] with optional grounded [context].
     * An empty context routes to the open-domain variant.
     */
    fun buildQA(question: String, context: String = ""): String =
        if (context.isBlank())
            render(Patterns.QUESTION_ANSWERING_OPEN, mapOf("question" to question)).prompt
        else
            render(
                Patterns.QUESTION_ANSWERING,
                mapOf("question" to question, "context" to context)
            ).prompt

    // ---------------------------------------------------------------------------
    // Quality scoring (APO-inspired heuristics, 0-10)
    // ---------------------------------------------------------------------------

    /**
     * Heuristically score a rendered prompt before sending it to the engine.
     *
     * Signals rewarded:
     *  - non-empty body, explicit task verb, section structure, constraint/rule words,
     *    and absence of unresolved `{{...}}` placeholders (heavy penalty).
     */
    fun scorePrompt(prompt: String): Double {
        var score = 0.0
        val t = prompt.trim()

        // Non-empty body
        if (t.isNotEmpty()) score += 2.0

        // Explicit task verb at the start of the instruction
        val taskVerbs = listOf("classify", "summar", "extract", "answer", "detect", "list", "translate", "review")
        if (taskVerbs.any { t.lowercase().startsWith(it) }) score += 2.0

        // Structural separation (sections / labeled fields)
        if (t.contains("\n\n") || Regex(":\\s*\\S").containsMatchIn(t)) score += 1.0

        // Constraint / rule wording
        val constraintPhrases = listOf("rules", "respond with", "only", "valid json", "null")
        if (constraintPhrases.any { t.lowercase().contains(it) }) score += 1.0

        // Few-shot examples present
        if (Regex("(?m)^\\s*Example[\\d:]", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 2.0

        // Unresolved placeholder penalty
        if (VAR_REGEX.containsMatchIn(t)) score -= 4.0

        return score.coerceIn(0.0, 10.0)
    }
}
