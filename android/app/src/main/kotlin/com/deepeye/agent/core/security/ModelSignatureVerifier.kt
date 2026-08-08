package com.deepeye.agent.core.security

import java.io.File
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory

/**
 * Verifies Ed25519 digital signatures for GGUF model files.
 *
 * A plain class (not object/singleton) so it can be dependency-injected and
 * easily replaced in tests.
 *
 * Signature convention: the `.sig` file lives at `${modelPath}.sig` and
 * contains the raw Ed25519 signature bytes over the full model file content.
 */
class ModelSignatureVerifier {

    /**
     * Verifies that [signaturePath] contains a valid Ed25519 signature over the
     * bytes of the model file at [modelPath], authenticated with [publicKeyBytes]
     * (X.509 / SubjectPublicKeyInfo DER-encoded).
     *
     * @return `true`  if the signature is present and cryptographically valid.
     *         `false` if the `.sig` file is missing, the model file is missing,
     *                 or the signature is invalid / tampered.
     */
    fun verifyModelSignature(
        modelPath: String,
        signaturePath: String,
        publicKeyBytes: ByteArray
    ): Boolean {
        val modelFile = File(modelPath)
        val sigFile = File(signaturePath)

        // Missing files → verification fails (returns false, does not throw)
        if (!modelFile.exists() || !sigFile.exists()) return false

        return try {
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("Ed25519")
            val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(modelFile.readBytes())

            sig.verify(sigFile.readBytes())
        } catch (_: Exception) {
            // Any crypto or I/O failure is treated as verification failure
            false
        }
    }
}
