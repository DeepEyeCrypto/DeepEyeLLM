package com.deepeye.agent.features.fileanalysis

import android.util.Log
import com.deepeye.agent.core.error.DeepEyeError
import com.deepeye.agent.core.error.ErrorMapper
import com.deepeye.agent.core.security.rbac.AccessControlLayer
import com.deepeye.agent.core.security.rbac.Permission
import com.deepeye.agent.policy.LocalSafetyContext
import com.deepeye.agent.policy.PolicyCheckLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudFileAnalysisService @Inject constructor(
    private val policyCheckLayer: PolicyCheckLayer,
    private val deepDebugApi: DeepDebugApi,
    private val accessControl: AccessControlLayer
) {

    suspend fun analyzeFileSecurely(
        file: File,
        mimeType: String,
        isOfflineModeEnabled: Boolean
    ): Result<DeepDebugResponse> = withContext(Dispatchers.IO) {
        try {
            // 0. RBAC enforcement — must have UPLOAD_FILES permission
            accessControl.enforce(Permission.UPLOAD_FILES)

            // 1. Construct local safety context
            val context = LocalSafetyContext(
                fileMimeType = mimeType,
                fileName = file.name,
                fileSizeBytes = file.length(),
                offlineMode = isOfflineModeEnabled
            )

            // 2. Evaluate against policy
            val decision = policyCheckLayer.evaluate(context)
            if (!decision.allowed) {
                Log.w("DeepEye-Analysis", "File analysis blocked by policy: ${decision.reason}")
                
                // Throw specific errors based on reason for better UX
                val error = if (decision.reason.contains("Offline mode")) {
                    DeepEyeError.OfflineModeRequired("Cloud File Analysis")
                } else if (decision.reason.contains("restricted")) {
                    DeepEyeError.RestrictedFileType(mimeType)
                } else {
                    DeepEyeError.Unknown("Policy Block: ${decision.reason}")
                }
                return@withContext Result.failure(Exception(error.userMessage, Throwable(error.technicalDetail)))
            }

            // 3. Prepare payload for API
            Log.d("DeepEye-Analysis", "Policy passed. Uploading ${file.name} for deep debug...")
            
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val jsonContext = """{"client":"Android-DeepEye", "offline_mode_requested": $isOfflineModeEnabled}"""
                .toRequestBody("application/json".toMediaTypeOrNull())

            // 4. Execute network call
            val response = deepDebugApi.analyzeFile(body, jsonContext)
            
            Log.d("DeepEye-Analysis", "Deep debug completed. Status: ${response.status}")
            Result.success(response)

        } catch (e: Exception) {
            Log.e("DeepEye-Analysis", "Deep debug failed", e)
            val mappedError = ErrorMapper.mapWithContext(e, "DeepDebug")
            Result.failure(Exception(mappedError.userMessage, e))
        }
    }
}
