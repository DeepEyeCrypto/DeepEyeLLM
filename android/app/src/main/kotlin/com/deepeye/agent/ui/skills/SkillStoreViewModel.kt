package com.deepeye.agent.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.skill.Skill
import com.deepeye.agent.core.skill.SkillRegistry
import com.deepeye.agent.data.network.SkillService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillStoreUiState(
    val isLoading: Boolean = false,
    val skills: List<Skill> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SkillStoreViewModel @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val skillService: SkillService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillStoreUiState())
    val uiState: StateFlow<SkillStoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            skillRegistry.communitySkills.collect { skills ->
                _uiState.value = _uiState.value.copy(skills = skills)
            }
        }
        // Initial fetch if empty
        if (skillRegistry.communitySkills.value.isEmpty()) {
            refreshSkills()
        }
    }

    fun refreshSkills() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = skillService.getCommunitySkills()
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val newSkills = response.body()!!
                    skillRegistry.updateSkills(newSkills)
                } else {
                    // Local fallback for offline/404 execution
                    skillRegistry.updateSkills(SkillRegistry.BUILTIN_SKILLS)
                }
            } catch (e: Exception) {
                // Local fallback for offline execution
                skillRegistry.updateSkills(SkillRegistry.BUILTIN_SKILLS)
                _uiState.update { it.copy(error = "Could not fetch remote skills. Showing cached skills.") }
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, skills = skillRegistry.communitySkills.value)
            }
        }
    }

    fun installSkill(skill: Skill, context: android.content.Context) {
        viewModelScope.launch {
            skillRegistry.markInstalled(skill.id)
            _uiState.value = _uiState.value.copy(skills = skillRegistry.communitySkills.value)
            android.widget.Toast.makeText(
                context,
                "Installed & Activated: ${skill.name}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
