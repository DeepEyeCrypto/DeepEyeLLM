# IMPLEMENTATION_ROADMAP.md

## Repository Structure

```
DeepEyeAgent/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml              # Build, test, lint
│   │   ├── security.yml        # SAST, DAST, SBOM
│   │   └── release.yml         # Sign, publish, canary
├── android/
│   ├── app/
│   │   ├── src/main/kotlin/
│   │   │   ├── core/
│   │   │   │   ├── LLMEngine.kt
│   │   │   │   ├── MemoryEngine.kt
│   │   │   │   ├── TaskRouter.kt
│   │   │   │   ├── SkillManager.kt
│   │   │   │   └── PolicyCache.kt
│   │   │   ├── features/
│   │   │   │   ├── chat/
│   │   │   │   ├── actions/
│   │   │   │   ├── ask-image/
│   │   │   │   ├── audio-scribe/
│   │   │   │   └── file-analysis/
│   │   │   ├── services/
│   │   │   │   ├── AgentService.kt
│   │   │   │   ├── SyncService.kt
│   │   │   │   └── CronService.kt
│   │   │   └── ui/
│   │   └── build.gradle.kts
│   └── shared-test/
├── ios/
│   ├── DeepEyeAgent/
│   │   ├── Core/
│   │   ├── Features/
│   │   ├── Services/
│   │   └── UI/
│   └── DeepEyeAgentTests/
├── desktop/
│   ├── src/
│   │   ├── main.rs
│   │   ├── lib.rs
│   │   └── ipc/
│   └── tauri.conf.json
├── shared/
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs
│       ├── memory.rs          # CozoDB bindings
│       ├── embeddings.rs      # On-device embedding model
│       ├── router.rs          # Task routing logic
│       ├── skills.rs          # Skill definitions + parser
│       ├── policy.rs          # OPA client + offline cache
│       └── crypto.rs          # Ed25519 signing + verification
├── cloud/
│   ├── gateway/
│   │   ├── main.py            # FastAPI gateway
│   │   ├── routers/
│   │   │   ├── skills.py
│   │   │   ├── analysis.py
│   │   │   └── health.py
│   │   └── Dockerfile
│   ├── hermes-bridge/
│   │   ├── bridge.py          # Hermes Agent integration
│   │   └── skills-sync.py     # Upstream sync service
│   └── tests/
├── models/
│   ├── configs/
│   │   ├── gemma-4-2b.yaml
│   │   ├── gemma-4-4b.yaml
│   │   └── function-gemma-270m.yaml
│   └── registry.json
├── policies/
│   ├── rego/
│   │   ├── routing.rego
│   │   ├── access.rego
│   │   └── data_class.rego
│   └── schemas/
│       ├── skill.schema.json
│       └── task.schema.json
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ADR/
│   └── API/
├── scripts/
│   ├── setup-dev.sh
│   ├── build-sbom.sh
│   └── sign-release.sh
├── config/
│   ├── aeos.yaml
│   └── regions.yaml
└── README.md
```

## Dependency Graph

```
[User Interface]
    |
    v
[Shared Core (Rust)] <---> [CozoDB]
    |                         |
    +---> [LiteRT/MNN]       +---> [Embedding Model]
    |                         |
    +---> [Policy Cache]      +---> [Skills Registry]
    |
    v
[Task Router] --(local)--> [On-Device Execution]
    |
    +--(cloud)--> [Cloud Gateway (FastAPI)]
                      |
                      v
              [Hermes Agent]
```

## API Contracts

### Mobile ↔ Shared Core (FFI)
- Language: C ABI via JNI (Android) / Swift FFI (iOS)
- Key functions:
  - `initialize_engine(config: Config) -> Result<Handle>`
  - `load_model(model_id: &str) -> Result<ModelHandle>`
  - `run_inference(handle: ModelHandle, prompt: &str) -> Result<String>`
  - `query_memory(query: &str, top_k: u32) -> Result<Vec<Memory>>`
  - `sync_skills() -> Result<Vec<SkillUpdate>>`

### Mobile ↔ Cloud Gateway
- Protocol: HTTPS (TLS 1.3) + protobuf / JSON
- Endpoints:
  - `POST /v1/skills/sync` — Request skill catalog update.
  - `POST /v1/analysis/deep-debug` — Upload file + context, receive structured report.
  - `POST /v1/skills/run` — Execute cloud skill with inputs.
  - `GET /v1/health` — Service health check.

### Cloud Gateway ↔ Hermes Agent
- Protocol: Local network / Unix socket / internal gRPC
- Authenticated via mTLS service identity.

## Database Design (CozoDB)

```datalog
:create memory {
    file_id: String,
    path: String =>
    hash: String,
    lang: String,
    chunk_id: String,
    embedding: <F32; 384>,
    content: String,
    created_at: Validity,
}

:create skill {
    skill_id: String =>
    version: String,
    description: String,
    inputs: {String},
    capabilities: {String},
    llm_prefs: {String},
    runs_on: String,
    source_url: String,
    updated_at: Validity,
}

:create policy_decision {
    decision_id: Uuid =>
    actor: String,
    action: String,
    resource: String,
    decision: String,
    policy_version: String,
    timestamp: DateTime,
}
```

## Microservice Boundaries

| Service | Responsibility | Location |
|---------|--------------|----------|
| Mobile App | UI, orchestration, offline-first logic | On-device |
| Shared Core | Inference, memory, routing, skills, policy, crypto | On-device (Rust lib) |
| Cloud Gateway | Skill sync, deep analysis, telemetry | Cloud (FastAPI) |
| Hermes Bridge | Skill execution, self-evolution, heavy tooling | Cloud (Python) |
| OPA Service | Policy evaluation, audit logging | Cloud / sidecar |

## Milestones

### Milestone 1: Foundation (Weeks 1-4)
- [ ] Repository setup with CI/CD.
- [ ] Shared Rust core skeleton (memory, router stubs).
- [ ] Android app shell with TBD UI.
- [ ] LiteRT integration for Gemma 4 2B.
- [ ] CozoDB embedded and accessible via FFI.

### Milestone 2: On-Device Agent (Weeks 5-8)
- [ ] Local chat with Gemma 4 2B/4B.
- [ ] Memory engine with embeddings and RAG.
- [ ] Task router (local-only mode).
- [ ] Mobile Actions (Android intents via FunctionGemma).
- [ ] Ask Image + Audio Scribe.

### Milestone 3: Hybrid Intelligence (Weeks 9-12)
- [ ] Cloud Gateway (FastAPI) deployed.
- [ ] Hermes bridge integration.
- [ ] Skill auto-sync pipeline.
- [ ] File analysis pipeline (local + cloud deep debug).
- [ ] OPA policy engine integrated.

### Milestone 4: Governance & Enterprise (Weeks 13-16)
- [ ] RBAC + approval workflows.
- [ ] Ed25519 signing + SBOM generation.
- [ ] STRIDE threat modeling completed.
- [ ] FAIR risk quantification dashboard.
- [ ] Canary release pipeline.

### Milestone 5: Multi-Platform & Polish (Weeks 17-20)
- [ ] iOS port (CoreML + CozoDB).
- [ ] Desktop (Tauri v2).
- [ ] Multi-LLM registry (Qwen, Llama, Phi).
- [ ] Performance optimization (quantization, caching).
- [ ] Community skill marketplace.

## Sprint Plan (First 4 Sprints)

| Sprint | Focus | Deliverables |
|--------|-------|--------------|
| Sprint 1 | Rust core + FFI | CozoDB bindings, embedding stub, Kotlin FFI tests |
| Sprint 2 | LiteRT + Gemma 4 | Model loading, tokenization, basic inference, benchmark |
| Sprint 3 | Memory + Router | CRUD, similarity search, routing logic, offline tests |
| Sprint 4 | Android UI + Actions | Chat UI, Mobile Actions, Ask Image stub |

---
[AEOS_STATUS: IMPLEMENTATION_PLANNED]
