package com.deepeye.agent.policy

import com.deepeye.agent.core.policy.PolicyAuditLog

data class LocalSafetyDecision(
    val allowed: Boolean,
    val reason: String = "Local safety check passed."
)

data class LocalSafetyContext(
    val fileMimeType: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val offlineMode: Boolean
)

class PolicyCheckLayer(
    val auditLog: PolicyAuditLog = PolicyAuditLog()
) {

    companion object {
        private val ALLOWED_MODEL_DOMAINS = setOf(
            "huggingface.co",
            "cdn-lfs.huggingface.co",
            "cdn-lfs-us-1.huggingface.co",
            "kaggle.com",
            "storage.googleapis.com",
            "github.com",
            "objects.githubusercontent.com"
        )

        const val DEFAULT_MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
    }

    fun evaluate(context: LocalSafetyContext): LocalSafetyDecision {
        val decision = evaluateInternal(context)
        auditLog.record(
            action = "file_analysis",
            allowed = decision.allowed,
            reason = decision.reason,
            context = mapOf(
                "fileName" to context.fileName,
                "mimeType" to context.fileMimeType,
                "sizeBytes" to context.fileSizeBytes.toString(),
                "offlineMode" to context.offlineMode.toString()
            )
        )
        return decision
    }

    private fun evaluateInternal(context: LocalSafetyContext): LocalSafetyDecision {
        if (!context.offlineMode) {
            return LocalSafetyDecision(
                allowed = false,
                reason = "Offline mode must stay enabled."
            )
        }

        if (isRestricted(context.fileMimeType)) {
            return LocalSafetyDecision(
                allowed = false,
                reason = "This file type is restricted for local analysis."
            )
        }

        if (context.fileSizeBytes <= 0L) {
            return LocalSafetyDecision(
                allowed = false,
                reason = "Invalid file size."
            )
        }

        return LocalSafetyDecision(allowed = true)
    }

    fun validateModelSource(url: String): LocalSafetyDecision {
        val decision = try {
            val host = java.net.URL(url).host.lowercase()
            val isAllowed = ALLOWED_MODEL_DOMAINS.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
            if (isAllowed) {
                LocalSafetyDecision(allowed = true, reason = "Model source is trusted: $host")
            } else {
                LocalSafetyDecision(allowed = false, reason = "Model source is not in the trusted domain list: $host")
            }
        } catch (e: Exception) {
            LocalSafetyDecision(allowed = false, reason = "Invalid model URL: ${e.message}")
        }
        auditLog.record(action = "validate_model_source", allowed = decision.allowed, reason = decision.reason, context = mapOf("url" to url))
        return decision
    }

    fun validateFileSize(bytes: Long, maxBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES): LocalSafetyDecision {
        val decision = if (bytes <= 0) {
            LocalSafetyDecision(allowed = false, reason = "Invalid file size: $bytes bytes")
        } else if (bytes > maxBytes) {
            LocalSafetyDecision(allowed = false, reason = "File too large: ${bytes / (1024 * 1024)}MB exceeds ${maxBytes / (1024 * 1024)}MB limit")
        } else {
            LocalSafetyDecision(allowed = true, reason = "File size within limits")
        }
        auditLog.record(action = "validate_file_size", allowed = decision.allowed, reason = decision.reason, context = mapOf("bytes" to bytes.toString(), "maxBytes" to maxBytes.toString()))
        return decision
    }

    private fun isRestricted(mimeType: String): Boolean {
        return mimeType.startsWith("application/vnd.android.package-archive") ||
            mimeType.startsWith("application/x-executable") ||
            mimeType.contains("secret", ignoreCase = true)
    }
}
