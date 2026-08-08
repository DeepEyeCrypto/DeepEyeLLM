package com.deepeye.agent.data.network

import com.deepeye.agent.core.skill.Skill
import retrofit2.Response
import retrofit2.http.GET

interface SkillService {
    /**
     * Fetches the latest list of curated community skills/agents.
     */
    @GET("https://raw.githubusercontent.com/deepeye/skills/main/skills.json") // Absolute URL overrides base
    suspend fun getCommunitySkills(): Response<List<Skill>>
}
