package com.deepeye.agent.core.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class UpstreamSyncManager : UpdateChecker {

    private var manifest = UpdateManifest()

    override suspend fun checkForUpdates(): List<SyncResult> = withContext(Dispatchers.IO) {
        Log.d("DeepEye", "{\"event\":\"update_check_started\", \"upstreams\":${manifest.upstreams.size}}")
        val results = mutableListOf<SyncResult>()
        val updatedUpstreams = mutableListOf<UpstreamSource>()

        for (source in manifest.upstreams) {
            if (!source.isEnabled) {
                updatedUpstreams.add(source)
                continue
            }

            try {
                // Determine API URL based on repoUrl. Assume GitHub for now.
                val apiUrl = if (source.repoUrl.contains("github.com")) {
                    val parts = source.repoUrl.split("github.com/")
                    if (parts.size > 1) {
                        val repoPath = parts[1]
                        "https://api.github.com/repos/$repoPath/releases/latest"
                    } else null
                } else null

                if (apiUrl != null) {
                    val url = URL(apiUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()

                        val json = JSONObject(response)
                        val tagName = json.optString("tag_name", source.currentVersion)
                        
                        val updatedSource = source.copy(
                            latestVersion = tagName,
                            lastSyncTimestamp = Instant.now()
                        )
                        updatedUpstreams.add(updatedSource)
                        
                        results.add(
                            SyncResult(
                                sourceId = source.id,
                                success = true,
                                message = "Found latest version: $tagName",
                                previousVersion = source.currentVersion,
                                newVersion = tagName
                            )
                        )
                    } else {
                        updatedUpstreams.add(source.copy(lastSyncTimestamp = Instant.now()))
                        results.add(
                            SyncResult(
                                sourceId = source.id,
                                success = false,
                                message = "HTTP error: ${connection.responseCode}"
                            )
                        )
                    }
                    connection.disconnect()
                } else {
                    updatedUpstreams.add(source.copy(lastSyncTimestamp = Instant.now()))
                    results.add(
                        SyncResult(
                            sourceId = source.id,
                            success = false,
                            message = "Unsupported repository URL format."
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("DeepEye", "Failed to check update for ${source.id}", e)
                updatedUpstreams.add(source.copy(lastSyncTimestamp = Instant.now()))
                results.add(
                    SyncResult(
                        sourceId = source.id,
                        success = false,
                        message = "Error: ${e.message}"
                    )
                )
            }
        }

        manifest = manifest.copy(
            upstreams = updatedUpstreams,
            lastFullSyncTimestamp = Instant.now()
        )
        
        Log.d("DeepEye", "{\"event\":\"update_check_completed\", \"results\":${results.size}}")
        results
    }

    override suspend fun applyUpdate(source: UpstreamSource): SyncResult {
        // Will be implemented when actually applying updates
        return SyncResult(
            sourceId = source.id,
            success = false,
            message = "Apply update not yet implemented."
        )
    }

    override suspend fun rollback(source: UpstreamSource): SyncResult {
        return SyncResult(
            sourceId = source.id,
            success = false,
            message = "Rollback not yet implemented."
        )
    }

    override fun getManifest(): UpdateManifest = manifest
}
