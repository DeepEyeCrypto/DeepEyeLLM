package com.deepeye.agent.core.security.rbac

import java.time.Instant

/**
 * Represents the currently active user session on this device.
 * In a production app, this would be populated from OIDC/OAuth2 token claims.
 */
data class UserSession(
    val userId: String,
    val displayName: String,
    val role: Role,
    val createdAt: Instant = Instant.now(),
    val isActive: Boolean = true
) {
    fun hasPermission(permission: Permission): Boolean =
        isActive && role.hasPermission(permission)
}
