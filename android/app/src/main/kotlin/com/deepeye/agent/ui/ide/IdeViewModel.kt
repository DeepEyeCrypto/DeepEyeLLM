package com.deepeye.agent.ui.ide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepeye.agent.core.ide.RooCodePatchAdapter
import com.deepeye.agent.core.ide.WorkspaceManager
import com.deepeye.agent.core.memory.HermesMemoryAdapter
import com.deepeye.agent.domain.EngineController
import com.deepeye.agent.domain.ModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class IdeUiState(
    val files: List<File> = emptyList(),
    val selectedFile: File? = null,
    val fileContent: String = "",
    val diffPreview: String? = null,
    val isAgentTyping: Boolean = false,
    val chatLog: List<String> = emptyList()
    // `prompt` is removed from global state to guarantee 0-latency typing layer
)

@HiltViewModel
class IdeViewModel @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    private val patchAdapter: RooCodePatchAdapter,
    private val memoryAdapter: HermesMemoryAdapter,
    private val engineController: EngineController
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    init {
        refreshFiles()
    }

    private fun refreshFiles() {
        _uiState.update { it.copy(files = workspaceManager.listFiles()) }
    }

    fun selectFile(file: File) {
        val relative = file.toRelativeString(workspaceManager.getWorkspaceRoot())
        val content = workspaceManager.readFile(relative) ?: ""
        _uiState.update { it.copy(selectedFile = file, fileContent = content, diffPreview = null) }
    }

    fun submitPrompt(prompt: String) {
        if (prompt.isBlank()) return

        _uiState.update { 
            it.copy(
                chatLog = it.chatLog + "User: $prompt",
                isAgentTyping = true
            )
        }

        viewModelScope.launch {
            val fileContent = _uiState.value.fileContent
            val contextMemories = memoryAdapter.getContext(prompt)
            
            val fullPrompt = buildString {
                append("You are an expert AI coder (Roo Code inside DeepEye).\n")
                if (contextMemories.isNotBlank()) {
                    append("$contextMemories\n")
                }
                if (fileContent.isNotBlank()) {
                    append("Current file content:\n```\n$fileContent\n```\n")
                }
                append("User instruction: $prompt\n")
                append("Output only the exact new text you want to replace or insert.")
            }

            // Await execution
            val result = engineController.executeChat(fullPrompt)
            val newText = if (result.first == ModelStatus.LOCAL_ACTIVE) result.second else "Error: ${result.second}"

            memoryAdapter.saveMemory("User asked for code change: $prompt")
            
            if (result.first == ModelStatus.LOCAL_ACTIVE) {
                // Generate diff preview
                val diff = patchAdapter.generateDiff(fileContent, newText)
                _uiState.update {
                    it.copy(
                        chatLog = it.chatLog + "Roo Code: I have generated a diff preview for your file.",
                        isAgentTyping = false,
                        diffPreview = diff
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        chatLog = it.chatLog + "Roo Code: $newText",
                        isAgentTyping = false
                    )
                }
            }
        }
    }

    fun applyPatch() {
        val selectedFile = _uiState.value.selectedFile ?: return
        val diff = _uiState.value.diffPreview ?: return
        
        // Very basic extraction of the replaced text for stub
        val parts = diff.split("====")
        if (parts.size >= 2) {
            val original = parts[0].replace("<<<< ORIGINAL\n", "").trim()
            val newText = parts[1].replace(">>>> REPLACED", "").trim()
            
            val relative = selectedFile.toRelativeString(workspaceManager.getWorkspaceRoot())
            patchAdapter.applyPatch(relative, original, newText)
            
            selectFile(selectedFile) // refresh
            _uiState.update { it.copy(chatLog = it.chatLog + "System: Patch applied successfully.") }
        }
    }
    
    fun createNewFile(name: String) {
        workspaceManager.writeFile(name, "// New file $name")
        refreshFiles()
    }
}
