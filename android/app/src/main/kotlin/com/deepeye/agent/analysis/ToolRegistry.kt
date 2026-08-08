package com.deepeye.agent.analysis

import java.io.File

class ToolRegistry {

    fun listProjectFiles(root: File): List<String> =
        root.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList()

    fun readFile(path: String): String = File(path).readText()

    fun detectMime(path: String): String = when {
        path.endsWith(".kt") -> "text/x-kotlin"
        path.endsWith(".java") -> "text/x-java"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".xml") -> "application/xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".wav") -> "audio/wav"
        else -> "application/octet-stream"
    }
}
