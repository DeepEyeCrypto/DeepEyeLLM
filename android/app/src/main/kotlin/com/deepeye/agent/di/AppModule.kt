package com.deepeye.agent.di

import com.deepeye.agent.analysis.ToolRegistry
import com.deepeye.agent.core.policy.PolicyAuditLog
import com.deepeye.agent.core.model.ModelRegistry
import com.deepeye.agent.core.update.UpdateChecker
import com.deepeye.agent.policy.PolicyCheckLayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Core application-level DI bindings.
 * Provides cross-cutting concerns: policy, tools, updates.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePolicyAuditLog(): PolicyAuditLog = PolicyAuditLog()

    @Provides
    @Singleton
    fun providePolicyCheckLayer(auditLog: PolicyAuditLog): PolicyCheckLayer =
        PolicyCheckLayer(auditLog)

    @Provides
    @Singleton
    fun provideToolRegistry(): ToolRegistry = ToolRegistry()

    @Provides
    @Singleton
    fun provideUpdateChecker(): UpdateChecker = com.deepeye.agent.core.update.RemoteUpdateChecker()

    @Provides
    @Singleton
    fun provideHermesDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): com.deepeye.agent.core.memory.HermesDatabase {
        return com.deepeye.agent.core.memory.HermesDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideMemoryDao(database: com.deepeye.agent.core.memory.HermesDatabase): com.deepeye.agent.core.memory.MemoryDao {
        return database.memoryDao()
    }

    @Provides
    @Singleton
    fun provideUserSession(): com.deepeye.agent.core.security.rbac.UserSession {
        // Mock session for Ring 1 internal builds.
        // In production, this would come from OIDC/OAuth2 token claims.
        return com.deepeye.agent.core.security.rbac.UserSession(
            userId = "dev-local",
            displayName = "Local Developer",
            role = com.deepeye.agent.core.security.rbac.Role.DEVELOPER
        )
    }

    @Provides
    @Singleton
    fun provideAccessControlLayer(
        session: com.deepeye.agent.core.security.rbac.UserSession,
        auditLog: PolicyAuditLog
    ): com.deepeye.agent.core.security.rbac.AccessControlLayer {
        return com.deepeye.agent.core.security.rbac.AccessControlLayer(session, auditLog)
    }

    @Provides
    @Singleton
    fun provideModelRegistry(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): ModelRegistry {
        return ModelRegistry.create(context)
    }

    @Provides
    @Singleton
    fun provideSkillService(retrofit: retrofit2.Retrofit): com.deepeye.agent.data.network.SkillService {
        return retrofit.create(com.deepeye.agent.data.network.SkillService::class.java)
    }
}
