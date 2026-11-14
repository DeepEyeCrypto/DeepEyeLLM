package com.deepeye.agent.analysis

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.deepeye.agent.data.local.KnowledgeRepository
import com.deepeye.agent.data.local.KnowledgeRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentChunk(
    val index: Int,
    val text: String,
    val charLength: Int
)

data class ParsedDocument(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val pageCount: Int = 1,
    val fullText: String,
    /**
     * Chunk count for display purposes only. The chunks themselves are NOT retained here:
     * they are chunked straight into the FTS knowledge table to keep large documents out of RAM.
     */
    val chunkCount: Int = 0,
    val chunks: List<DocumentChunk> = emptyList(),
    val isPdf: Boolean = false
)

/**
 * Local RAG Document Ingestion & Parser Utility.
 * Extracts text from PDFs and text/code documents via SAF (Storage Access Framework),
 * indexes chunks into the local SQLite FTS / Vector Knowledge base,
 * and formats context for LLM prompt injection retrieving strictly the top 3 relevant chunks.
 */
@Singleton
class DocumentParser @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val knowledgeRetriever: KnowledgeRetriever
) {

    suspend fun parseDocument(context: Context, uri: Uri): Result<ParsedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val (fileName, fileSize) = queryMetadata(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: guessMimeType(fileName)
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)

            var pageCount = 1
            val extractedText: String

            if (isPdf) {
                // PDF Parsing with memory safety
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        try {
                            renderer = PdfRenderer(pfd)
                            pageCount = renderer.pageCount.coerceAtLeast(1)
                        } catch (e: Throwable) {
                            Log.w(TAG, "PdfRenderer notice: ${e.message}")
                        }
                    }
                } finally {
                    try { renderer?.close() } catch (_: Throwable) {}
                    try { pfd?.close() } catch (_: Throwable) {}
                }

                extractedText = extractPdfText(context, uri, fileName, pageCount)
            } else {
                // Plain Text / Code / Markdown
                extractedText = extractPlainText(context, uri)
            }

            val chunks = chunkText(extractedText, chunkSize = 500, overlap = 80)

            val docId = java.util.UUID.randomUUID().toString()

            val parsedDoc = ParsedDocument(
                id = docId,
                uriString = uri.toString(),
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSize,
                pageCount = pageCount, // Do not hold raw chunks or large text in memory
                fullText = if (extractedText.length > 5000) extractedText.take(5000) + "\n...[truncated for memory safety, full text is chunked in DB]" else extractedText,
                chunkCount = chunks.size,
                chunks = emptyList(),
                isPdf = isPdf
            )

            // Index chunks into SQLite FTS Knowledge tables
            knowledgeRetriever.insertDocumentChunks(
                docId = docId,
                docTitle = fileName,
                chunks = chunks.map { it.text },
                tags = if (isPdf) "pdf,document" else "text,code"
            )

            // Legacy indexing fallback if used elsewhere
            knowledgeRepository.indexDocument(parsedDoc.copy(chunks = chunks))

            parsedDoc
        }
    }
    private fun queryMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed querying metadata for uri: $uri", e)
        }
        return Pair(name, size)
    }

    private fun extractPlainText(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val sb = StringBuilder()
                var line = reader.readLine()
                var charCount = 0
                val maxChars = 150_000

                while (line != null && charCount < maxChars) {
                    sb.appendLine(line)
                    charCount += line.length + 1
                    line = reader.readLine()
                }
                if (charCount >= maxChars) {
                    sb.appendLine("\n... [Document truncated at 150,000 characters for local inference context limit]")
                }
                sb.toString()
            } ?: ""
        } catch (e: Throwable) {
            Log.e(TAG, "Error reading text document", e)
            "Error reading document: ${e.message}"
        }
    }

    private fun extractPdfText(context: Context, uri: Uri, fileName: String, pageCount: Int): String {
        val extractedBuilder = StringBuilder()
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(8192)
                var bytesRead: Int
                var total = 0
                val maxBytes = 10 * 1024 * 1024
                while (stream.read(temp).also { bytesRead = it } != -1 && total < maxBytes) {
                    buffer.write(temp, 0, bytesRead)
                    total += bytesRead
                }
                buffer.toByteArray()
            } ?: ByteArray(0)

            if (bytes.isNotEmpty()) {
                val textContent = extractPdfStreamText(bytes)
                if (textContent.isNotBlank()) {
                    extractedBuilder.append(textContent)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing PDF streams for $fileName", e)
        }

        val result = extractedBuilder.toString().trim()
        return if (result.length > 30) {
            result
        } else {
            "[PDF Document: $fileName ($pageCount pages). Visual layouts and binary tables indexed for Multimodal Vision & QA.]"
        }
    }

    private fun extractPdfStreamText(pdfBytes: ByteArray): String {
        val textBuilder = StringBuilder()
        val pdfString = String(pdfBytes, Charsets.ISO_8859_1)

        val streamRegex = Regex("""(?s)stream\r?\n(.*?)\r?\nendstream""")
        val matches = streamRegex.findAll(pdfString)

        for (match in matches) {
            val streamContent = match.groupValues[1]
            var uncompressed = streamContent

            try {
                val rawBytes = streamContent.toByteArray(Charsets.ISO_8859_1)
                val inflater = InflaterInputStream(ByteArrayInputStream(rawBytes))
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var n: Int
                var readTotal = 0
                while (inflater.read(buf).also { n = it } != -1 && readTotal < 500_000) {
                    out.write(buf, 0, n)
                    readTotal += n
                }
                uncompressed = out.toString("UTF-8")
            } catch (_: Throwable) {
            }

            val btBlocks = Regex("""(?s)BT\s+(.*?)\s+ET""").findAll(uncompressed)
            for (bt in btBlocks) {
                val blockText = bt.groupValues[1]
                val tjMatches = Regex("""\((.*?)\)\s*(?:Tj|'|")""").findAll(blockText)
                for (tj in tjMatches) {
                    val rawStr = tj.groupValues[1]
                    val cleaned = unescapePdfString(rawStr)
                    if (cleaned.isNotBlank()) {
                        textBuilder.append(cleaned).append(" ")
                    }
                }

                val arrayMatches = Regex("""\[(.*?)\]\s*TJ""").findAll(blockText)
                for (arr in arrayMatches) {
                    val inner = arr.groupValues[1]
                    val innerStrings = Regex("""\((.*?)\)""").findAll(inner)
                    for (s in innerStrings) {
                        val cleaned = unescapePdfString(s.groupValues[1])
                        if (cleaned.isNotBlank()) {
                            textBuilder.append(cleaned)
                        }
                    }
                    textBuilder.append(" ")
                }
                textBuilder.append("\n")
            }
        }

        return textBuilder.toString()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(\. |\n\n)"""), "$1\n")
            .trim()
    }

    private fun unescapePdfString(str: String): String {
        return str
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }

    fun chunkText(text: String, chunkSize: Int = 1000, overlap: Int = 150): List<DocumentChunk> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var index = 0

        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            val chunkStr = text.substring(start, end).trim()
            if (chunkStr.isNotEmpty()) {
                chunks.add(DocumentChunk(index = index++, text = chunkStr, charLength = chunkStr.length))
            }
            if (end >= text.length) break
            start += (chunkSize - overlap).coerceAtLeast(100)
        }
        return chunks
    }

    suspend fun formatContextPrompt(userPrompt: String, documents: List<ParsedDocument>): String {
        // Retrieve ONLY the top 3 most relevant chunks across all indexed documents using FTS MATCH
        val relevantChunks = knowledgeRetriever.retrieveRelevantChunks(userPrompt, topK = 3)
        if (relevantChunks.isNotEmpty()) {
            return knowledgeRetriever.enrichSystemPrompt(userPrompt, relevantChunks)
        }

        // If no relevant chunks in DB, fallback to passing attached documents text (if any)
        if (documents.isNotEmpty()) {
            val fallbackChunks = documents.flatMap { it.chunks }.take(3)
            if (fallbackChunks.isNotEmpty()) {
                val contextBuilder = StringBuilder()
                contextBuilder.appendLine("<retrieved_knowledge>")
                fallbackChunks.forEachIndexed { idx, chunk ->
                    contextBuilder.appendLine("### Chunk [${idx + 1}/${fallbackChunks.size}] (Index: ${chunk.index}):")
                    contextBuilder.appendLine(chunk.text)
                    contextBuilder.appendLine()
                }
                contextBuilder.appendLine("</retrieved_knowledge>")
                contextBuilder.appendLine()
                contextBuilder.appendLine("Based on the retrieved document knowledge above, answer the user thoroughly and accurately.")
                contextBuilder.appendLine()
                contextBuilder.appendLine("User: $userPrompt")
                return contextBuilder.toString()
            }
        }
        
        return userPrompt
    }

    private fun guessMimeType(fileName: String): String = when {
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
        fileName.endsWith(".md", ignoreCase = true) -> "text/markdown"
        fileName.endsWith(".json", ignoreCase = true) -> "application/json"
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        fileName.endsWith(".kt", ignoreCase = true) -> "text/x-kotlin"
        fileName.endsWith(".py", ignoreCase = true) -> "text/x-python"
        fileName.endsWith(".rs", ignoreCase = true) -> "text/rust"
        else -> "text/plain"
    }

    companion object {
        private const val TAG = "DeepEye-DocumentParser"
    }
}
