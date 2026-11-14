package com.deepeye.agent.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Execution status for agentic tool calls and function invocations.
 */
@Serializable
enum class ToolCallStatus {
    EXECUTING,
    SUCCESS,
    FAILED,
    AWAITING_APPROVAL
}

/**
 * Data model representing an on-device or agentic tool call execution.
 *
 * @property id Unique tool call identifier.
 * @property name Name of the function/tool (e.g. "web_search", "adb_command", "dex_swap").
 * @property arguments Stringified JSON or human-readable parameters passed to the tool.
 * @property result Output or error message returned by the tool execution.
 * @property status Current lifecycle state of the invocation.
 * @property durationMs Execution latency in milliseconds.
 */
@Immutable
@Serializable
data class ToolCallModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val arguments: String = "",
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.EXECUTING,
    val durationMs: Long? = null
)
