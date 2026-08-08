package com.deepeye.agent.core.security.rbac

/**
 * Granular permissions that can be assigned to roles.
 * Each permission maps to a specific agent action that requires authorization.
 */
enum class Permission(val description: String) {
    // Sync & Update
    SYNC_SKILLS("Sync skills and updates from upstream sources"),
    APPLY_UPDATE("Apply upstream updates to local agent"),
    ROLLBACK_UPDATE("Rollback to a previous stable version"),

    // File Operations
    UPLOAD_FILES("Upload files to the Cloud Gateway for deep debug"),
    READ_FILES("Read files from local storage for analysis"),

    // Memory
    CLEAR_MEMORY("Clear the Hermes memory database"),
    READ_MEMORY("Query the Hermes memory database"),
    WRITE_MEMORY("Write new entries to memory"),

    // Model Management
    DOWNLOAD_MODEL("Download new AI models to device"),
    DELETE_MODEL("Delete AI models from device"),
    LOAD_MODEL("Load a model into the inference engine"),

    // Administration
    VIEW_AUDIT_LOG("View the policy audit log"),
    MANAGE_ROLES("Manage user roles and permissions"),
    ACCESS_AUTOMATION("Use ADB automation hooks"),

    // Secrets
    READ_SECRETS("Access sensitive configuration or API keys"),
}
