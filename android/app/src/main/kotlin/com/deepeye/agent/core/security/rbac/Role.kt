package com.deepeye.agent.core.security.rbac

/**
 * Predefined roles with default permission sets.
 * Follows the principle of least privilege — each role only gets what it needs.
 */
enum class Role(val displayName: String, val permissions: Set<Permission>) {

    ADMIN(
        displayName = "Administrator",
        permissions = Permission.entries.toSet() // Full access
    ),

    DEVELOPER(
        displayName = "Developer",
        permissions = setOf(
            Permission.SYNC_SKILLS,
            Permission.APPLY_UPDATE,
            Permission.ROLLBACK_UPDATE,
            Permission.UPLOAD_FILES,
            Permission.READ_FILES,
            Permission.CLEAR_MEMORY,
            Permission.READ_MEMORY,
            Permission.WRITE_MEMORY,
            Permission.DOWNLOAD_MODEL,
            Permission.DELETE_MODEL,
            Permission.LOAD_MODEL,
            Permission.VIEW_AUDIT_LOG,
            Permission.ACCESS_AUTOMATION,
        )
    ),

    USER(
        displayName = "Standard User",
        permissions = setOf(
            Permission.READ_FILES,
            Permission.READ_MEMORY,
            Permission.WRITE_MEMORY,
            Permission.LOAD_MODEL,
            Permission.VIEW_AUDIT_LOG,
        )
    ),

    GUEST(
        displayName = "Guest",
        permissions = setOf(
            Permission.READ_FILES,
            Permission.READ_MEMORY,
        )
    );

    fun hasPermission(permission: Permission): Boolean = permission in permissions
}
