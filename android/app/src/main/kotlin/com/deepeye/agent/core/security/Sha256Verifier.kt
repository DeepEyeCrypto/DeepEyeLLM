package com.deepeye.agent.core.security

import java.io.File
import java.security.MessageDigest

/**
 * High-performance streaming SHA-256 verification utility for multi-gigabyte GGUF models.
 */
object Sha256Verifier {

    fun calculateSha256(file: File, bufferSize: Int = 131072): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(bufferSize).use { input ->
            val buffer = ByteArray(bufferSize)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || expectedSha256.isBlank()) return false
        val actual = calculateSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
