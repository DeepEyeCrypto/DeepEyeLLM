package com.deepeye.agent.core.security

import com.deepeye.agent.domain.engine.LlamaCppEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * Unit tests for [ModelSignatureVerifier] and the Ed25519 verification gate
 * wired into [LlamaCppEngine.init].
 *
 * Validates: Requirements 1.5, 1.6, 8.7
 */
class ModelSignatureVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Generates a fresh Ed25519 key pair and returns (publicKeyDerBytes, privateKey). */
    private fun generateEd25519KeyPair(): Pair<ByteArray, java.security.PrivateKey> {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        val kp = kpg.generateKeyPair()
        return Pair(kp.public.encoded, kp.private)
    }

    /** Signs [data] with [privateKey] using Ed25519 and returns the signature bytes. */
    private fun sign(data: ByteArray, privateKey: java.security.PrivateKey): ByteArray {
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    /** Creates a model file with [size] random bytes and returns it. */
    private fun createModelFile(name: String = "model.gguf", size: Int = 1_500_000): File {
        val f = tempFolder.newFile(name)
        f.writeBytes(ByteArray(size) { it.toByte() })
        return f
    }

    // ── ModelSignatureVerifier unit tests ────────────────────────────────────

    @Test
    fun `verifyModelSignature returns true for a valid signature`() {
        val (pubKeyBytes, privKey) = generateEd25519KeyPair()
        val modelFile = createModelFile()
        val sigFile = File(modelFile.absolutePath + ".sig")
        sigFile.writeBytes(sign(modelFile.readBytes(), privKey))

        val verifier = ModelSignatureVerifier()
        assertTrue(
            verifier.verifyModelSignature(
                modelPath = modelFile.absolutePath,
                signaturePath = sigFile.absolutePath,
                publicKeyBytes = pubKeyBytes
            )
        )
    }

    @Test
    fun `verifyModelSignature returns false when model content is tampered`() {
        val (pubKeyBytes, privKey) = generateEd25519KeyPair()
        val modelFile = createModelFile()
        // Sign original content …
        val sigFile = File(modelFile.absolutePath + ".sig")
        sigFile.writeBytes(sign(modelFile.readBytes(), privKey))

        // … then corrupt one byte in the model
        val bytes = modelFile.readBytes()
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        modelFile.writeBytes(bytes)

        val verifier = ModelSignatureVerifier()
        assertFalse(
            verifier.verifyModelSignature(
                modelPath = modelFile.absolutePath,
                signaturePath = sigFile.absolutePath,
                publicKeyBytes = pubKeyBytes
            )
        )
    }

    @Test
    fun `verifyModelSignature returns false when sig file is missing`() {
        val (pubKeyBytes, _) = generateEd25519KeyPair()
        val modelFile = createModelFile()
        val missingSigPath = modelFile.absolutePath + ".sig"   // file never created

        val verifier = ModelSignatureVerifier()
        assertFalse(
            verifier.verifyModelSignature(
                modelPath = modelFile.absolutePath,
                signaturePath = missingSigPath,
                publicKeyBytes = pubKeyBytes
            )
        )
    }

    @Test
    fun `verifyModelSignature returns false when model file is missing`() {
        val (pubKeyBytes, privKey) = generateEd25519KeyPair()
        val missingModelPath = File(tempFolder.root, "ghost.gguf").absolutePath
        val sigFile = tempFolder.newFile("ghost.gguf.sig")
        sigFile.writeBytes(sign(ByteArray(16), privKey))  // irrelevant content

        val verifier = ModelSignatureVerifier()
        assertFalse(
            verifier.verifyModelSignature(
                modelPath = missingModelPath,
                signaturePath = sigFile.absolutePath,
                publicKeyBytes = pubKeyBytes
            )
        )
    }

    @Test
    fun `verifyModelSignature returns false for signature made with a different key`() {
        val (pubKeyBytes, _) = generateEd25519KeyPair()
        val (_, differentPrivKey) = generateEd25519KeyPair()

        val modelFile = createModelFile()
        val sigFile = File(modelFile.absolutePath + ".sig")
        sigFile.writeBytes(sign(modelFile.readBytes(), differentPrivKey))

        val verifier = ModelSignatureVerifier()
        assertFalse(
            verifier.verifyModelSignature(
                modelPath = modelFile.absolutePath,
                signaturePath = sigFile.absolutePath,
                publicKeyBytes = pubKeyBytes
            )
        )
    }

    // ── LlamaCppEngine integration tests ─────────────────────────────────────

    @Test
    fun `LlamaCppEngine init succeeds when signature is valid`() = runBlocking {
        val (pubKeyBytes, privKey) = generateEd25519KeyPair()
        val modelFile = createModelFile()
        val sigFile = File(modelFile.absolutePath + ".sig")
        sigFile.writeBytes(sign(modelFile.readBytes(), privKey))

        val engine = LlamaCppEngine(
            modelPath = modelFile.absolutePath,
            modelPublicKeyBytes = pubKeyBytes,
            skipSignatureCheck = false
        )
        val result = engine.init()
        assertTrue("Engine init should succeed with valid signature", result.isSuccess)
        assertTrue(engine.isInitialized)
    }

    @Test
    fun `LlamaCppEngine init fails with SecurityException when signature is invalid`() = runBlocking {
        val (pubKeyBytes, _) = generateEd25519KeyPair()
        val (_, differentPrivKey) = generateEd25519KeyPair()

        val modelFile = createModelFile()
        val sigFile = File(modelFile.absolutePath + ".sig")
        // Sign with a different key so verification will fail
        sigFile.writeBytes(sign(modelFile.readBytes(), differentPrivKey))

        val engine = LlamaCppEngine(
            modelPath = modelFile.absolutePath,
            modelPublicKeyBytes = pubKeyBytes,
            skipSignatureCheck = false
        )
        val result = engine.init()
        assertTrue("Engine init should fail when signature is invalid", result.isFailure)
        assertFalse(engine.isInitialized)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(
            result.exceptionOrNull()!!.message!!.contains("Ed25519 signature verification failed")
        )
    }

    @Test
    fun `LlamaCppEngine init fails with SecurityException when sig file is missing`() = runBlocking {
        val (pubKeyBytes, _) = generateEd25519KeyPair()
        val modelFile = createModelFile()
        // No .sig file created

        val engine = LlamaCppEngine(
            modelPath = modelFile.absolutePath,
            modelPublicKeyBytes = pubKeyBytes,
            skipSignatureCheck = false
        )
        val result = engine.init()
        assertTrue("Engine init should fail when .sig file is absent", result.isFailure)
        assertFalse(engine.isInitialized)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `LlamaCppEngine init skips verification when skipSignatureCheck is true`() = runBlocking {
        // No key provided, no .sig file — verification must be entirely skipped
        val modelFile = createModelFile()

        val engine = LlamaCppEngine(
            modelPath = modelFile.absolutePath,
            modelPublicKeyBytes = null,
            skipSignatureCheck = true
        )
        val result = engine.init()
        assertTrue("Engine init should succeed when skipSignatureCheck=true", result.isSuccess)
        assertTrue(engine.isInitialized)
    }

    @Test
    fun `LlamaCppEngine init skips verification when no public key is provided`() = runBlocking {
        // modelPublicKeyBytes = null with skipSignatureCheck = false → no verification
        val modelFile = createModelFile()

        val engine = LlamaCppEngine(
            modelPath = modelFile.absolutePath,
            modelPublicKeyBytes = null,
            skipSignatureCheck = false
        )
        val result = engine.init()
        assertTrue("Engine init should succeed when no public key is configured", result.isSuccess)
        assertTrue(engine.isInitialized)
    }
}
