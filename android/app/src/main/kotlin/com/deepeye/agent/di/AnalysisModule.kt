package com.deepeye.agent.di

import android.content.Context
import com.deepeye.agent.DeepEyeAgentEngine
import com.deepeye.agent.analysis.FileAnalysisService
import com.deepeye.agent.analysis.ToolRegistry
import com.deepeye.agent.ui.history.HistoryLocalStore
import com.deepeye.agent.ui.history.HistoryItemUi
import com.deepeye.agent.ui.history.HistoryRepository
import com.deepeye.agent.ui.history.HistoryRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI module for file analysis and history.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {

    @Provides
    @Singleton
    fun provideFileAnalysisService(
        @ApplicationContext context: Context,
        engine: DeepEyeAgentEngine,
        toolRegistry: ToolRegistry
    ): FileAnalysisService {
        return FileAnalysisService(context, engine, toolRegistry)
    }

    @Provides
    @Singleton
    fun provideHistoryLocalStore(): HistoryLocalStore {
        // Stub store — will be replaced with Room in Phase 3
        return object : HistoryLocalStore {
            override suspend fun getAll(): List<HistoryItemUi> = emptyList()
            override suspend fun delete(id: String) {}
            override suspend fun export(id: String) {}
        }
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(store: HistoryLocalStore): HistoryRepository {
        return HistoryRepositoryImpl(store)
    }
}
