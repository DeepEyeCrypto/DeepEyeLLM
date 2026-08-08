package com.deepeye.agent.di

import android.content.Context
import com.deepeye.agent.DeepEyeAgentEngine
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.engine.LLMEngine
import com.deepeye.agent.core.datastore.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * DI module for the inference engine and model management.
 * Engine and controller are singletons — expensive to create.
 *
 * NOTE: Engine instances are cheap to construct — native library loading
 * is deferred to [LLMEngine.init] which is always called from Dispatchers.IO.
 * This prevents Hilt singleton initialization from blocking the main thread
 * and triggering the BLAST sync ANR during Activity startup.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideDeepEyeAgentEngine(
        @ApplicationContext context: Context
    ): DeepEyeAgentEngine {
        // Construction only stores the path — no native lib loading here.
        // loadNativeLibIfNeeded() is called lazily inside init() on Dispatchers.IO.
        val modelPath = File(context.filesDir, "model.bin").absolutePath
        return DeepEyeAgentEngine(modelPath)
    }

    @Provides
    @Singleton
    fun provideLLMEngine(
        engine: DeepEyeAgentEngine
    ): LLMEngine = engine

    @Provides
    @Singleton
    fun provideEngineController(
        engine: LLMEngine,
        @ApplicationContext context: Context,
        settingsDataStore: SettingsDataStore
    ): EngineController {
        return EngineController(engine, context, settingsDataStore)
    }

}
