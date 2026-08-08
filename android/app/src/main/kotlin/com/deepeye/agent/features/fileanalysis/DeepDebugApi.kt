package com.deepeye.agent.features.fileanalysis

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class DeepDebugResponse(
    val status: String,
    val reportId: String,
    val findings: List<String>,
    val recommendation: String
)

interface DeepDebugApi {
    @Multipart
    @POST("/v1/analysis/deep-debug")
    suspend fun analyzeFile(
        @Part file: MultipartBody.Part,
        @Part("context") context: RequestBody
    ): DeepDebugResponse
}
