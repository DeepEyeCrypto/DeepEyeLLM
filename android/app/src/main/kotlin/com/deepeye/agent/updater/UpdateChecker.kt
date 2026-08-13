package com.deepeye.agent.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import com.deepeye.agent.BuildConfig

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String
)

@Singleton
class UpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val GITHUB_OWNER = "DeepEyeCrypto"
        private const val GITHUB_REPO = "DeepEyeLLM"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdate(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
            }

            val bodyString = response.body?.string() ?: throw Exception("Empty response body")
            val json = JSONObject(bodyString)

            val tagName = json.getString("tag_name") // e.g. "v2.0.0" or "2027.2.0"
            val body = json.optString("body", "No changelog provided.")
            
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                return@withContext Result.failure(Exception("No APK found in release assets"))
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isVersionNewer(currentVersion, tagName)

            Result.success(UpdateInfo(isNewer, tagName, downloadUrl, body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        // Strip 'v' prefix if present
        val currStr = current.removePrefix("v").trim()
        val latestStr = latest.removePrefix("v").trim()

        if (currStr == latestStr) return false

        val currParts = currStr.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latestStr.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(currParts.size, latestParts.size)
        for (i in 0 until length) {
            val currPart = currParts.getOrNull(i) ?: 0
            val latestPart = latestParts.getOrNull(i) ?: 0
            if (latestPart > currPart) return true
            if (latestPart < currPart) return false
        }
        return false
    }
}
