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
class GitHubUpdateChecker @Inject constructor(
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
                .header("User-Agent", "DeepEyeLLM-Android/${BuildConfig.VERSION_NAME}")
                .build()

            android.util.Log.d("DeepEyeUpdate", "Checking GitHub releases: $RELEASES_API_URL")
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("DeepEyeUpdate", "GitHub API error: ${response.code}")
                return@withContext Result.failure(Exception("GitHub API error (${response.code})"))
            }

            val bodyString = response.body?.string() ?: throw Exception("Empty response body from GitHub API")
            val json = JSONObject(bodyString)

            val tagName = json.getString("tag_name") // e.g. "v2.2.0"
            val body = json.optString("body", "DeepEyeLLM latest release.")
            
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                // Fallback to HTML release URL if direct APK asset is not yet attached
                downloadUrl = json.optString("html_url", "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases")
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isVersionNewer(currentVersion, tagName)
            android.util.Log.d("DeepEyeUpdate", "Update check result: current=$currentVersion, latest=$tagName, isNewer=$isNewer, url=$downloadUrl")

            Result.success(UpdateInfo(isNewer, tagName, downloadUrl, body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        val currClean = current.removePrefix("v").removePrefix("V").trim()
        val latestClean = latest.removePrefix("v").removePrefix("V").trim()

        if (currClean.equals(latestClean, ignoreCase = true)) return false

        // Extract semantic integer parts (ignoring build numbers if any)
        val currParts = currClean.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        val latestParts = latestClean.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

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
