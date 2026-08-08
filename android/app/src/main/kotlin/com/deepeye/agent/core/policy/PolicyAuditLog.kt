package com.deepeye.agent.core.policy

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

data class PolicyAuditEntry(
    val timestamp: Instant = Instant.now(),
    val action: String,
    val allowed: Boolean,
    val reason: String,
    val context: Map<String, String> = emptyMap()
)

class PolicyAuditLog(private val maxEntries: Int = 500) {

    private val entries = ConcurrentLinkedDeque<PolicyAuditEntry>()

    fun record(entry: PolicyAuditEntry) {
        entries.addFirst(entry)
        while (entries.size > maxEntries) {
            entries.removeLast()
        }
    }

    fun record(action: String, allowed: Boolean, reason: String, context: Map<String, String> = emptyMap()) {
        record(PolicyAuditEntry(action = action, allowed = allowed, reason = reason, context = context))
    }

    fun getRecent(count: Int = 50): List<PolicyAuditEntry> =
        entries.take(count)

    fun getAll(): List<PolicyAuditEntry> =
        entries.toList()

    fun getByAction(action: String): List<PolicyAuditEntry> =
        entries.filter { it.action == action }

    fun getDenied(): List<PolicyAuditEntry> =
        entries.filter { !it.allowed }

    fun clear() = entries.clear()

    val size: Int get() = entries.size
}
