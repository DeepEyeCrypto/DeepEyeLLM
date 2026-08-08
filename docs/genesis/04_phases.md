# 04 — Implementation Roadmap & Phases: DeepEyeLLM

## Phase 0: Day-0 Genesis Bootstrap [COMPLETED]
- Establish 6 foundational specification files (`01_prd.md` to `06_memory.md`).
- Validate Control/Data plane isolation rules and architecture boundaries.

---

## Phase 1: Core Navigation & LiteRT Engine [COMPLETED]
- Wire Jetpack Compose scaffold, `AgentAppShell`, `AgentNavigation`, and Material3 Adaptive navigation.
- Integrate Google AI Edge LiteRT (`litertlm_jni`) engine for `.bin` flatbuffers.
- Build `ChatScreen`, `ChatViewModel`, and input capsule UI.

---

## Phase 2: GGUF Native Inference Spike & State Hoisting [COMPLETED]
- Define polymorphic `LLMEngine` interface (`init`, `chat`, `chatStream`, `close`).
- Implement `LlamaCppEngine` for native GGUF execution (`.gguf` models: Qwen 3 1.7B / 4B).
- Refactor `EngineController` for dynamic engine selection based on file extension.
- Hoist state in `ModelManagerScreen` and `ModelCatalogViewModel` with dynamic RAM detection.
- Add unit test suite (`LlamaCppEngineTest`, `ModelDownloaderTest`) passing clean in CI/JVM.
- On-device validation on physical hardware (`ZD2226X6RW`).

---

## Phase 3: Hermes Cognitive Memory & Skills Engine [NEXT]
- **Milestone 3.1**: Implement Room DB `HermesDatabase` with `MemoryDao` for persistent episodic session storage.
- **Milestone 3.2**: Build Skill Dispatch Pipeline for automated file inspection, code diffing, and system diagnostics.
- **Milestone 3.3**: Integrate Hermes agent memory recall into prompt generation context.

---

## Phase 4: Roo Code IDE & Mobile AST Editor
- **Milestone 4.1**: Implement zero-latency `TypingArea` with sub-1ms keydown handler.
- **Milestone 4.2**: Integrate AST file viewer and unified code diff previewer.
- **Milestone 4.3**: Implement automated surgical patch applier with undo/redo stack.

---

## Phase 5: Termux / proot Diagnostic Shell Integration
- **Milestone 5.1**: Establish policy-gated local shell execution container (`proot-Ubuntu`).
- **Milestone 5.2**: Add diagnostic script runner and automated system health auditor.
- **Milestone 5.3**: Production release hardening, security audit, and final performance profiling.
