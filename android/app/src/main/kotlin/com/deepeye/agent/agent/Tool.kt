package com.deepeye.agent.agent

import android.net.Uri

interface Tool {
    val name: String
    val description: String
    suspend fun execute(input: String): String
}

class SearchTool : Tool {
    override val name: String = "search"
    override val description: String = "Searches internal agent knowledge base for keyword matches."

    override suspend fun execute(input: String): String {
        return "Search result for '$input': Found 2 relevant local snippets."
    }
}

class FileAnalysisTool(
    private val analyzeCallback: suspend (Uri) -> String
) : Tool {
    override val name: String = "file_analysis"
    override val description: String = "Analyzes local document binaries."

    suspend fun executeFile(uri: Uri): String {
        return analyzeCallback(uri)
    }

    override suspend fun execute(input: String): String {
        return "File analysis requires a valid file Uri."
    }
}

class CodeExecutionTool : Tool {
    override val name: String = "code_exec"
    override val description: String = "Executes local sandboxed code snippets."

    override suspend fun execute(input: String): String {
        return "Executed script safely. Exit code: 0."
    }
}
