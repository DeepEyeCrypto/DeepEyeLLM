package com.deepeye.agent.core.security

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Unit tests for [Sha256Verifier] validating streaming SHA-256 calculation and verification.
 */
class Sha256VerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculateSha256 computes expected hash for known text`() {
        val file = tempFolder.newFile("sample.txt")
        file.writeText("DeepEyeLLM-Edge-Inference-Engine")

        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest("DeepEyeLLM-Edge-Inference-Engine".toByteArray())
            .joinToString("") { "%02x".format(it) }

        val actual = Sha256Verifier.calculateSha256(file)
        assertEquals(expectedDigest, actual)
    }

    @Test
    fun `verify returns true for matching sha256 hash`() {
        val file = tempFolder.newFile("model.gguf")
        val sampleData = ByteArray(256 * 1024) { (it % 128).toByte() }
        file.writeBytes(sampleData)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sampleData)
            .joinToString("") { "%02x".format(it) }

        assertTrue(Sha256Verifier.verify(file, digest))
        assertTrue(Sha256Verifier.verify(file, digest.uppercase()))
    }

    @Test
    fun `verify returns false for corrupted or tampered file`() {
        val file = tempFolder.newFile("corrupted_model.gguf")
        val sampleData = ByteArray(64 * 1024) { 1.toByte() }
        file.writeBytes(sampleData)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sampleData)
            .joinToString("") { "%02x".format(it) }

        // Corrupt one byte
        sampleData[0] = 99.toByte()
        file.writeBytes(sampleData)

        assertFalse(Sha256Verifier.verify(file, digest))
    }

    @Test
    fun `verify returns false for missing file or blank hash`() {
        val nonExistent = File(tempFolder.root, "ghost_file.gguf")
        assertFalse(Sha256Verifier.verify(nonExistent, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))

        val realFile = tempFolder.newFile("blank_hash.txt")
        realFile.writeText("test")
        assertFalse(Sha256Verifier.verify(realFile, ""))
        assertFalse(Sha256Verifier.verify(realFile, "   "))
    }
}
