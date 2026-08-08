package com.deepeye.agent.core.update

import android.util.Log

interface UpdateChecker {
    suspend fun checkForUpdates(): List<SyncResult>
    suspend fun applyUpdate(source: UpstreamSource): SyncResult
    suspend fun rollback(source: UpstreamSource): SyncResult
    fun getManifest(): UpdateManifest
}

/**
 * Stub implementation for Phase 1.
 * Real sync logic will be implemented in Phase 6.
 */
class StubUpdateChecker : UpdateChecker {

    private var manifest = UpdateManifest()

    override suspend fun checkForUpdates(): List<SyncResult> {
        Log.d("DeepEye", "{\"event\":\"update_check_started\", \"upstreams\":${manifest.upstreams.size}}")
        // Stub: no real network calls yet
        return manifest.upstreams.map { source ->
            SyncResult(
                sourceId = source.id,
                success = true,
                message = "No updates available (stub)",
                previousVersion = source.currentVersion,
                newVersion = source.currentVersion
            )
        }.also {
            Log.d("DeepEye", "{\"event\":\"update_check_completed\", \"results\":${it.size}}")
        }
    }

    override suspend fun applyUpdate(source: UpstreamSource): SyncResult {
        Log.d("DeepEye", "{\"event\":\"update_apply_skipped\", \"source\":\"${source.id}\", \"reason\":\"stub_implementation\"}")
        return SyncResult(
            sourceId = source.id,
            success = false,
            message = "Updates not yet implemented (Phase 6)"
        )
    }

    override suspend fun rollback(source: UpstreamSource): SyncResult {
        Log.d("DeepEye", "{\"event\":\"rollback_skipped\", \"source\":\"${source.id}\", \"reason\":\"stub_implementation\"}")
        return SyncResult(
            sourceId = source.id,
            success = false,
            message = "Rollback not yet implemented (Phase 6)"
        )
    }

    override fun getManifest(): UpdateManifest = manifest
}
