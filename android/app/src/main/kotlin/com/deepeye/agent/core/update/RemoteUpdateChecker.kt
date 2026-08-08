package com.deepeye.agent.core.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class RemoteUpdateChecker(
    private val client: OkHttpClient = OkHttpClient()
) : UpdateChecker {

    private var manifest = UpdateManifest()

    override suspend fun checkForUpdates(): List<SyncResult> = withContext(Dispatchers.IO) {
        Log.d("DeepEye-Sync", "Checking for remote updates for ${manifest.upstreams.size} sources")
        
        manifest.upstreams.map { source ->
            try {
                // For GitHub repos, we query the latest release API
                val url = if (source.repoUrl.contains("github.com")) {
                    val path = source.repoUrl.replace("https://github.com/", "")
                    "https://api.github.com/repos/$path/releases/latest"
                } else {
                    source.repoUrl
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "DeepEyeLLM-Agent")
                    .build()

                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    
                    val remoteVersion = json.optString("tag_name", "unknown").removePrefix("v")
                    
                    val hasUpdate = remoteVersion != source.currentVersion && remoteVersion != "unknown"
                    
                    SyncResult(
                        sourceId = source.id,
                        success = true,
                        message = if (hasUpdate) "Update available: $remoteVersion" else "Up to date",
                        previousVersion = source.currentVersion,
                        newVersion = remoteVersion
                    )
                } else {
                    SyncResult(
                        sourceId = source.id,
                        success = false,
                        message = "HTTP ${response.code}: ${response.message}"
                    )
                }
            } catch (e: IOException) {
                SyncResult(
                    sourceId = source.id,
                    success = false,
                    message = "Network error: ${e.message}"
                )
            } catch (e: Exception) {
                SyncResult(
                    sourceId = source.id,
                    success = false,
                    message = "Error parsing response: ${e.message}"
                )
            }
        }.also { results ->
            Log.d("DeepEye-Sync", "Sync completed. Results: ${results.size}")
        }
    }

    override suspend fun applyUpdate(source: UpstreamSource): SyncResult {
        // In a full implementation, this would download the ZIP/APK, verify signature, and install.
        Log.d("DeepEye-Sync", "Applying update for ${source.id}")
        return SyncResult(
            sourceId = source.id,
            success = true,
            message = "Update applied successfully (Simulated for Phase 6)"
        )
    }

    override suspend fun rollback(source: UpstreamSource): SyncResult {
        Log.d("DeepEye-Sync", "Rolling back ${source.id}")
        return SyncResult(
            sourceId = source.id,
            success = true,
            message = "Rolled back successfully (Simulated for Phase 6)"
        )
    }

    override fun getManifest(): UpdateManifest = manifest
}
