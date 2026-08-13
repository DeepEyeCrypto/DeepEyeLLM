package com.deepeye.agent.updater

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    fun downloadUpdate(url: String): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0))

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP Error: ${response.code}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }

            val contentLength = body.contentLength()
            
            // Create updates directory in external cache
            val updatesDir = File(context.externalCacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            
            val outputFile = File(updatesDir, "update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(8 * 1024)
            var bytesCopied: Long = 0
            var bytesRead: Int
            var lastProgress = 0

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        if (contentLength > 0) {
                            val progress = ((bytesCopied * 100) / contentLength).toInt()
                            if (progress > lastProgress) {
                                lastProgress = progress
                                emit(DownloadState.Progress(progress))
                            }
                        }
                    }
                }
            }

            emit(DownloadState.Success(outputFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadState {
    data class Progress(val percent: Int) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
