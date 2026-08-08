package com.deepeye.agent.core.update

import java.time.Instant

data class UpstreamSource(
    val id: String,
    val name: String,
    val repoUrl: String,
    val branch: String = "main",
    val lastSyncTimestamp: Instant? = null,
    val currentVersion: String = "unknown",
    val latestVersion: String? = null,
    val isEnabled: Boolean = true
)

data class UpdateManifest(
    val appVersion: String = "2027.2.0",
    val manifestVersion: Int = 1,
    val upstreams: List<UpstreamSource> = DEFAULT_UPSTREAMS,
    val lastFullSyncTimestamp: Instant? = null
) {
    companion object {
        val DEFAULT_UPSTREAMS = listOf(
            UpstreamSource(
                id = "ai_edge_gallery",
                name = "Google AI Edge Gallery",
                repoUrl = "https://github.com/google-ai-edge/gallery",
                branch = "main"
            ),
            UpstreamSource(
                id = "hermes",
                name = "Hermes Agent",
                repoUrl = "https://github.com/anthropics/hermes",
                branch = "main"
            ),
            UpstreamSource(
                id = "roo_code",
                name = "Roo Code",
                repoUrl = "https://github.com/RooVetGit/Roo-Code",
                branch = "main"
            ),
            UpstreamSource(
                id = "termux",
                name = "Termux",
                repoUrl = "https://github.com/termux/termux-app",
                branch = "master"
            )
        )
    }
}

data class SyncResult(
    val sourceId: String,
    val success: Boolean,
    val message: String,
    val changedFiles: List<String> = emptyList(),
    val previousVersion: String? = null,
    val newVersion: String? = null,
    val timestamp: Instant = Instant.now(),
    val rollbackAvailable: Boolean = false
)

data class RollbackInfo(
    val sourceId: String,
    val fromVersion: String,
    val toVersion: String,
    val backedUpFiles: List<String>,
    val timestamp: Instant = Instant.now()
)
