package com.deepeye.agent.core.security.rbac

import android.util.Log
import com.deepeye.agent.core.error.DeepEyeError
import com.deepeye.agent.core.policy.PolicyAuditLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central RBAC enforcer. All privileged operations must pass through this layer.
 *
 * Usage:
 *   accessControlLayer.enforce(Permission.UPLOAD_FILES)
 *   // if we reach here, access is granted
 */
@Singleton
class AccessControlLayer @Inject constructor(
    private val session: UserSession,
    private val auditLog: PolicyAuditLog
) {

    /**
     * Checks if the current session has the required permission.
     * @return true if allowed, false if denied.
     */
    fun check(permission: Permission): Boolean {
        val allowed = session.hasPermission(permission)
        auditLog.record(
            action = "rbac_check:${permission.name}",
            allowed = allowed,
            reason = if (allowed) {
                "Role '${session.role.displayName}' has permission ${permission.name}"
            } else {
                "Role '${session.role.displayName}' lacks permission ${permission.name}"
            },
            context = mapOf(
                "userId" to session.userId,
                "role" to session.role.name,
                "permission" to permission.name
            )
        )
        return allowed
    }

    /**
     * Enforces the required permission. Throws if denied.
     * Use this in service methods that MUST be gated.
     */
    fun enforce(permission: Permission) {
        if (!check(permission)) {
            Log.w(
                "DeepEye-RBAC",
                "ACCESS DENIED: User '${session.userId}' (${session.role.name}) " +
                    "attempted ${permission.name}"
            )
            val error = DeepEyeError.AccessDenied(
                userId = session.userId,
                requiredPermission = permission.name
            )
            throw SecurityException(error.userMessage)
        }
        Log.d(
            "DeepEye-RBAC",
            "ACCESS GRANTED: ${session.userId} -> ${permission.name}"
        )
    }

    /**
     * Returns the current session info for UI display.
     */
    fun currentSession(): UserSession = session
}
