# 05_API_GOVERNANCE

## POLICY GATE
The API Governance layer sits between the UI/Shell and the upstream Adapters. It enforces offline-first and zero-trust policies before any action occurs.

### Rules of Engagement
1. **No Direct Upstream Calls from UI**: The UI must request actions via the `SyncManager` or `ModelManager`, never directly calling HTTP APIs or upstream code.
2. **Offline Inference Enforcement**: The Edge Adapter will reject any inference request if network requirements are detected. All model execution must use native C++ / TFLite / local runtimes.
3. **Download Verification Policy**: A file cannot transition from `Verifying` to `Installed` unless the computed SHA-256 hash strictly matches the manifest `checksum`.

## ADAPTER CONTRACTS

### Inference API (Edge Adapter)
```kotlin
interface LocalInferenceEngine {
    fun loadModel(modelId: String, localPath: String): Flow<EngineState>
    fun executeInference(prompt: PromptContext): Flow<InferenceResult>
    fun unloadModel()
}
```

### Agent API (Hermes Adapter)
```kotlin
interface AgentMemoryManager {
    fun fetchWorkflowPlan(taskId: String): WorkflowPlan
    fun updateSkillState(skillId: String, newContext: SkillContext)
}
```

### Vibe Coding API (Roo Code Adapter)
```kotlin
interface VibeCodingEngine {
    fun generatePatch(prompt: String, files: List<FileContext>): PatchResult
    fun applyPatchAtomic(patch: PatchResult): Result<Unit>
    fun getDiff(fileId: String): DiffResult
}
```

## SYNC ROUTING
- When a manual "Check for Updates" is triggered, the `SyncManager` queries both the Google AI Edge releases and the Hermes registry.
- If updates are found, they are routed through the `SchemaValidator` before being persisted to the `Data Mesh`.
