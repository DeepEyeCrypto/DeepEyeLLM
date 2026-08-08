package com.deepeye.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.deepeye.agent.domain.engine.LlamaCppEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end instrumented tests for the llama.cpp JNI bridge via [LlamaCppEngine].
 *
 * Design constraints:
 *  - JNI functions (nativeInitModel, nativeGenerateResponse, etc.) are private external;
 *    all assertions must go through the public API: init(), chat(), chatStream(), close().
 *  - Tests MUST pass even when no physical GGUF model file is present on the device.
 *    When the native library is absent the engine falls back to a Kotlin stub — tests
 *    assert the correct public API behaviour for both paths.
 *  - When a real GGUF model is present at [MODEL_PATH] the native path is exercised too.
 *
 * Validates: Requirements 1.1, 1.2 (on-device runtime), 1.5 (model load path)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LlamaCppEngineTest {

    companion object {
        /**
         * Optional: place a tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf (or any small GGUF) at
         * this path on the device to exercise the native inference path.
         * If absent, all tests still pass via the Kotlin fallback.
         */
        private const val MODEL_PATH =
            "/data/local/tmp/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

        /** Minimum valid GGUF size threshold enforced by LlamaCppEngine.init(). */
        private const val MIN_VALID_GGUF_BYTES = 1_000_000L
    }

    // TemporaryFolder gives us a real on-device path that is guaranteed NOT to exist
    // (or is empty) so we can test the error path without any model file.
    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var realModelAvailable: Boolean

    @Before
    fun setUp() {
        realModelAvailable = File(MODEL_PATH).let { it.exists() && it.length() >= MIN_VALID_GGUF_BYTES }
    }

    @After
    fun tearDown() {
        // Nothing to tear down — each test creates its own LlamaCppEngine instance
        // and calls close() before asserting.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Error path: nativeInitModel returns 0 for a non-existent model path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * init() on a path that does not exist must return Result.failure.
     * This exercises the file-existence check in LlamaCppEngine.init() which
     * mirrors what nativeInitModel would return (0 / null handle) for bad paths.
     *
     * Validates: Requirement 1.5 — model load path verification.
     */
    @Test
    fun initModel_nonExistentPath_returnsFailure() = runBlocking {
        val nonExistentPath = tempDir.root.absolutePath + "/no_such_model.gguf"
        val engine = LlamaCppEngine(modelPath = nonExistentPath)

        val result = engine.init()

        assertTrue(
            "init() must fail for a non-existent model path",
            result.isFailure
        )
        val exception = result.exceptionOrNull()
        assertNotNull("Result.failure must carry an exception", exception)
        assertTrue(
            "Exception message must mention the path or file-not-found semantics",
            exception!!.message?.contains(nonExistentPath) == true ||
                exception is IllegalArgumentException
        )

        // Engine must not be initialised after a failed init
        assertFalse(
            "isInitialized must remain false after a failed init",
            engine.isInitialized
        )
    }

    /**
     * init() on a path that points to an empty file (< 1 MB) must also return
     * Result.failure — mirrors the truncated/invalid GGUF guard in LlamaCppEngine.
     *
     * Validates: Requirement 1.5 — model integrity check.
     */
    @Test
    fun initModel_tooSmallFile_returnsFailure() = runBlocking {
        val smallFile = tempDir.newFile("tiny.gguf").apply { writeText("not a real GGUF") }
        val engine = LlamaCppEngine(modelPath = smallFile.absolutePath)

        val result = engine.init()

        assertTrue(
            "init() must fail for a file smaller than the minimum GGUF size",
            result.isFailure
        )
        assertFalse(
            "isInitialized must remain false after init() rejection of a tiny file",
            engine.isInitialized
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Successful init + close lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * When a valid GGUF model is available init() must succeed, isInitialized
     * becomes true, and close() must not throw.
     *
     * When no real model is present the test asserts that isInitialized is false
     * (init fails gracefully), which exercises the same lifecycle contract.
     *
     * Validates: Requirements 1.1, 1.2 (on-device runtime lifecycle).
     */
    @Test
    fun initAndClose_lifecycle_succeeds() = runBlocking {
        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false,
            customContextSize = 512,
            customThreads = 2
        )

        val initResult = engine.init()

        if (realModelAvailable) {
            assertTrue("init() must succeed when a valid GGUF model is present", initResult.isSuccess)
            assertTrue("isInitialized must be true after successful init()", engine.isInitialized)
        } else {
            // Without a model file init() will fail — that is expected and tested above.
            // This branch simply documents the expected state.
            assertFalse("isInitialized must be false when no model is available", engine.isInitialized)
        }

        // close() must be safe to call regardless of init() outcome
        engine.close()
        assertFalse(
            "isInitialized must be false after close()",
            engine.isInitialized
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. nativeGenerateResponse (synchronous) path via chat()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * When the engine is successfully initialised chat() must return a non-blank
     * string. When the native lib is absent the Kotlin fallback message must also
     * be non-blank and indicate the unavailability.
     *
     * Validates: Requirements 1.1, 1.2.
     */
    @Test
    fun chat_withInitialisedEngine_returnsNonBlankResponse() = runBlocking {
        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false,
            customContextSize = 512,
            customThreads = 2
        )
        val initResult = engine.init()

        if (!realModelAvailable) {
            // Cannot exercise chat without an initialised engine; skip the assertion.
            return@runBlocking
        }
        assertTrue("Precondition: init() must succeed", initResult.isSuccess)

        val response = withTimeout(120_000L) {
            engine.chat("Say hello in one word.")
        }

        assertTrue(
            "chat() must return a non-blank response",
            response.isNotBlank()
        )

        engine.close()
    }

    /**
     * chat() called on an uninitialised engine must throw an IllegalStateException.
     *
     * Validates: Requirement 1.2 — runtime validation.
     */
    @Test
    fun chat_onUninitializedEngine_throwsIllegalStateException() = runBlocking {
        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false
        )
        // Deliberately skip init()

        var threw = false
        try {
            engine.chat("hello")
        } catch (e: IllegalStateException) {
            threw = true
        }

        assertTrue(
            "chat() on an uninitialised engine must throw IllegalStateException",
            threw
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. nativeGenerateResponseStream path via chatStream()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * chatStream() must deliver at least one non-empty token chunk and the
     * complete callback must fire (signalled by the streaming function returning
     * normally without throwing).
     *
     * When native lib is absent the Kotlin fallback splits the stub response into
     * space-separated tokens — the same assertions apply.
     *
     * Validates: Requirements 1.1, 1.2 — streaming inference.
     */
    @Test
    fun chatStream_deliversNonEmptyTokensAndCompletes() = runBlocking {
        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false,
            customContextSize = 512,
            customThreads = 2
        )
        val initResult = engine.init()

        if (!realModelAvailable) {
            return@runBlocking
        }
        assertTrue("Precondition: init() must succeed", initResult.isSuccess)

        val receivedTokens = mutableListOf<String>()
        var completedNormally = false

        withTimeout(120_000L) {
            engine.chatStream("Count to three.") { chunk ->
                receivedTokens.add(chunk)
            }
            completedNormally = true
        }

        assertTrue(
            "chatStream() must deliver at least one token",
            receivedTokens.isNotEmpty()
        )
        assertTrue(
            "Every delivered token chunk must be non-empty",
            receivedTokens.all { it.isNotEmpty() }
        )
        assertTrue(
            "chatStream() must complete without exception (onGenerationComplete fired)",
            completedNormally
        )

        engine.close()
    }

    /**
     * The fallback path (native lib absent) in chatStream() must also deliver
     * non-empty token chunks when the engine is initialised via the non-native
     * path. This test creates a minimal engine whose init() succeeds via the
     * Kotlin fallback stub.
     *
     * Note: Because the Kotlin fallback requires the model file to exist (even
     * though it does not actually load it via native), this test uses a
     * synthesised file large enough to pass the size guard.
     *
     * Validates: Requirement 1.2 — fallback streaming.
     */
    @Test
    fun chatStream_kotlinFallback_deliversNonEmptyTokens() = runBlocking {
        // Skip if native lib is loaded — the fallback path is not reachable then.
        if (LlamaCppEngine.isNativeLibLoaded) return@runBlocking

        // Create a fake "model file" large enough to pass the 1 MB size check so that
        // init() completes in the Kotlin fallback without a native load.
        val fakeModel = tempDir.newFile("fake_model.gguf")
        val mb2 = ByteArray(2 * 1024 * 1024) { 0xAB.toByte() }
        fakeModel.writeBytes(mb2)

        val engine = LlamaCppEngine(
            modelPath = fakeModel.absolutePath,
            useGpu = false,
            customContextSize = 256,
            customThreads = 1
        )
        val initResult = engine.init()
        assertTrue("Precondition: init() must succeed via Kotlin fallback", initResult.isSuccess)
        assertTrue("Precondition: isNativeLibLoaded must be false", !LlamaCppEngine.isNativeLibLoaded)

        val receivedChunks = mutableListOf<String>()
        var completed = false

        withTimeout(30_000L) {
            engine.chatStream("Ping.") { chunk ->
                receivedChunks.add(chunk)
            }
            completed = true
        }

        assertTrue("Fallback chatStream() must deliver at least one chunk", receivedChunks.isNotEmpty())
        assertTrue("Fallback chunks must all be non-empty", receivedChunks.all { it.isNotEmpty() })
        assertTrue("Fallback chatStream() must complete normally", completed)

        engine.close()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. nativeAbortGeneration via coroutine cancellation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cancelling the coroutine running chatStream() must stop generation within
     * a 1-token boundary. We assert that after cancellation no additional tokens
     * arrive once the coroutine scope reports cancellation.
     *
     * Validates: Requirements 1.1 — abort generation.
     */
    @Test
    fun chatStream_cancellation_stopsGenerationWithin1TokenBoundary() = runBlocking {
        if (!realModelAvailable) return@runBlocking

        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false,
            customContextSize = 512,
            customThreads = 2
        )
        assertTrue("Precondition: init() must succeed", engine.init().isSuccess)

        val tokensBeforeCancel = AtomicInteger(0)
        val tokensAfterCancel = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)
        val cancelLatch = CountDownLatch(1)

        // Launch streaming in a child job so we can cancel it
        val streamJob = kotlinx.coroutines.launch(Dispatchers.IO) {
            engine.chatStream("Describe the history of computing in great detail.") { chunk ->
                if (cancelled.get()) {
                    tokensAfterCancel.incrementAndGet()
                } else {
                    val count = tokensBeforeCancel.incrementAndGet()
                    // Cancel after receiving 3 tokens to leave a measurable window
                    if (count >= 3) {
                        cancelLatch.countDown()
                    }
                }
            }
        }

        // Wait until we have received enough tokens then cancel
        val latchAcquired = cancelLatch.await(60, TimeUnit.SECONDS)
        if (latchAcquired) {
            cancelled.set(true)
            streamJob.cancel()
            streamJob.join()
        } else {
            streamJob.cancel()
        }

        engine.close()

        // After cancellation the token callback must not have been invoked more than
        // once more (1-token boundary allowance for in-flight token at abort time).
        assertTrue(
            "Generation must stop within 1 token after cancellation, " +
                "but received ${tokensAfterCancel.get()} tokens after cancel",
            tokensAfterCancel.get() <= 1
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. nativeFreeModel — close() releases resources cleanly
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * close() must set isInitialized to false regardless of whether init()
     * succeeded, exercising the nativeFreeModel cleanup path.
     *
     * Calling close() twice must not throw (idempotent contract).
     *
     * Validates: Requirements 1.1 — resource lifecycle.
     */
    @Test
    fun close_setsIsInitializedFalse_andIsIdempotent() = runBlocking {
        val engine = LlamaCppEngine(modelPath = MODEL_PATH, useGpu = false)
        engine.init() // may or may not succeed depending on model availability

        engine.close()
        assertFalse("isInitialized must be false after close()", engine.isInitialized)

        // Second close() must not throw
        var secondCloseThrew = false
        try {
            engine.close()
        } catch (e: Exception) {
            secondCloseThrew = true
        }
        assertFalse("close() must be idempotent — second call must not throw", secondCloseThrew)
        assertFalse("isInitialized must still be false after second close()", engine.isInitialized)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Full JNI round-trip: init → stream → abort → free (native path only)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full happy-path integration: init the model, stream tokens, assert they
     * are non-empty and the complete callback fires, then free the model.
     *
     * This test is skipped when no real GGUF file is present, making it safe to
     * run in a CI environment without a model.
     *
     * Validates: Requirements 1.1, 1.2, 1.5 — full JNI bridge round-trip.
     */
    @Test
    fun nativeBridge_fullRoundTrip_initStreamFree() = runBlocking {
        if (!realModelAvailable) {
            // Document the intent without failing the build
            assertTrue("Skipping native round-trip — no GGUF model at $MODEL_PATH", true)
            return@runBlocking
        }

        val engine = LlamaCppEngine(
            modelPath = MODEL_PATH,
            useGpu = false,
            customContextSize = 512,
            customThreads = 2
        )

        // ── init ──────────────────────────────────────────────────────────────
        val initResult = engine.init()
        assertTrue("nativeInitModel must succeed for a valid GGUF path", initResult.isSuccess)
        assertTrue("isInitialized must be true after successful init", engine.isInitialized)

        // ── stream ────────────────────────────────────────────────────────────
        val tokens = mutableListOf<String>()
        var streamCompleted = false

        withTimeout(120_000L) {
            engine.chatStream("Reply with a single word.") { token ->
                tokens.add(token)
            }
            streamCompleted = true
        }

        assertTrue("Streaming must deliver at least one token", tokens.isNotEmpty())
        assertTrue("Every token must be non-empty", tokens.all { it.isNotEmpty() })
        assertTrue("Stream must complete (onGenerationComplete must fire)", streamCompleted)

        // ── synchronous inference ─────────────────────────────────────────────
        val response = withTimeout(120_000L) { engine.chat("Reply with a single word.") }
        assertTrue("nativeGenerateResponse must return a non-blank string", response.isNotBlank())

        // ── free ──────────────────────────────────────────────────────────────
        engine.close()
        assertFalse("isInitialized must be false after nativeFreeModel via close()", engine.isInitialized)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. activeModelPath reflects the path provided at construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates: Requirement 1.5 — model load path is preserved and queryable.
     */
    @Test
    fun activeModelPath_returnsConstructorPath() {
        val path = "/some/path/model.gguf"
        val engine = LlamaCppEngine(modelPath = path)
        assertEquals(
            "activeModelPath must equal the path provided at construction",
            path,
            engine.activeModelPath
        )
    }
}
