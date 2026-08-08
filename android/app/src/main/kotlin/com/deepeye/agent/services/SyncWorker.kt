package com.deepeye.agent.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepeye.agent.core.security.rbac.AccessControlLayer
import com.deepeye.agent.core.security.rbac.Permission
import com.deepeye.agent.core.update.UpdateChecker
import com.deepeye.agent.core.skill.SkillRegistry
import com.deepeye.agent.data.network.SkillService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val accessControl: AccessControlLayer,
    private val skillService: SkillService,
    private val skillRegistry: SkillRegistry
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("DeepEye-SyncWorker", "Background sync started...")
        try {
            // RBAC enforcement — must have SYNC_SKILLS permission
            accessControl.enforce(Permission.SYNC_SKILLS)

            val results = updateChecker.checkForUpdates()
            
            val hasUpdates = results.any { it.success && it.message.contains("Update available") }
            if (hasUpdates) {
                Log.d("DeepEye-SyncWorker", "Updates found! (Simulating notification push)")
                // In a production app, dispatch a NotificationManager push notification here
            } else {
                Log.d("DeepEye-SyncWorker", "All sources up to date.")
            }
            // Fetch community skills (Community Ecosystem)
            Log.d("DeepEye-SyncWorker", "Fetching community skills from cloud...")
            val response = skillService.getCommunitySkills()
            if (response.isSuccessful) {
                val skills = response.body() ?: emptyList()
                skillRegistry.updateSkills(skills)
                Log.d("DeepEye-SyncWorker", "Successfully synced ${skills.size} skills.")
            } else {
                Log.w("DeepEye-SyncWorker", "Failed to fetch skills. HTTP ${response.code()}")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("DeepEye-SyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
