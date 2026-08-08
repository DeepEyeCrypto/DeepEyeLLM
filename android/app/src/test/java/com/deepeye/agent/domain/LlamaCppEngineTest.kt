package com.deepeye.agent.domain

import com.deepeye.agent.core.model.ModelBackend
import com.deepeye.agent.domain.engine.LlamaCppEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlamaCppEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLlamaCppEngineInitializationSuccess() = runBlocking {
        // Create a dummy GGUF file (> 1MB)
        val dummyGgufFile = tempFolder.newFile("qwen3-1.7b-q4_k_m.gguf")
        val dummyData = ByteArray(1_500_000)
        dummyGgufFile.writeBytes(dummyData)

        val engine = LlamaCppEngine(dummyGgufFile.absolutePath)
        assertEquals(ModelBackend.GGUF_LLAMA_CPP, engine.backend)
        assertFalse(engine.isInitialized)

        val result = engine.init()
        assertTrue(result.isSuccess)
        assertTrue(engine.isInitialized)
        assertEquals(dummyGgufFile.absolutePath, engine.activeModelPath)

        val response = engine.chat("Hello from GGUF test")
        assertNotNull(response)
        assertTrue(response.isNotEmpty())

        engine.close()
        assertFalse(engine.isInitialized)
    }

    @Test
    fun testLlamaCppEngineInitFailsOnMissingFile() = runBlocking {
        val nonExistentPath = File(tempFolder.root, "non_existent.gguf").absolutePath
        val engine = LlamaCppEngine(nonExistentPath)

        val result = engine.init()
        assertTrue(result.isFailure)
        assertFalse(engine.isInitialized)
    }
}
