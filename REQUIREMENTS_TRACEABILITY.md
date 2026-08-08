# REQUIREMENTS_TRACEABILITY.md

## Requirement Classification

### Functional Requirements (FR)

| ID | Requirement | Priority | Epic | Feature | Task | Test |
|----|-------------|----------|------|---------|------|------|
| FR-001 | App shall run open-source LLMs locally without internet. | P0 | On-Device Inference | Local Model Runtime | Integrate LiteRT/MNN | T-INFER-001 |
| FR-002 | App shall support Gemma 4 family (2B/4B/12B/27B). | P0 | On-Device Inference | Model Registry | Add Gemma 4 loader | T-INFER-002 |
| FR-003 | App shall support Qwen 2.5, Llama 3, Phi-3, and other quantized GGUF models. | P1 | On-Device Inference | Multi-Model Support | Add generic GGUF loader | T-INFER-003 |
| FR-004 | App shall provide local memory with cross-session recall (CozoDB). | P0 | Memory | Local Memory Engine | Integrate CozoDB | T-MEM-001 |
| FR-005 | App shall store embeddings locally for RAG (no cloud vector DB). | P0 | Memory | On-Device RAG | Add embedding model + index | T-MEM-002 |
| FR-006 | App shall route tasks between on-device and cloud based on complexity/privacy/cost. | P0 | Routing | Task Router | Implement routing logic | T-ROUT-001 |
| FR-007 | App shall auto-sync skill catalog from Hermes Agent upstream. | P0 | Skills | Skill Sync | Build sync pipeline | T-SKILL-001 |
| FR-008 | App shall parse Hermes skills into canonical JSON schema. | P0 | Skills | Skill Parser | Write SKILL.md parser | T-SKILL-002 |
| FR-009 | App shall support autonomous skill creation and self-improvement. | P1 | Skills | Skill Evolution | Port DSPy+GEPA loop | T-SKILL-003 |
| FR-010 | App shall execute Mobile Actions (Android intents, settings, apps). | P1 | Actions | Mobile Actions | Integrate FunctionGemma | T-ACT-001 |
| FR-011 | App shall analyze any file type (code, log, binary, image, audio). | P0 | Analysis | File Analysis Pipeline | Build file importer + RAG | T-ANAL-001 |
| FR-012 | App shall perform deep debugging via cloud Hermes bridge. | P1 | Analysis | Cloud Debug | Build Hermes HTTP bridge | T-ANAL-002 |
| FR-013 | App shall support Ask Image (vision/OCR) on-device. | P1 | Multimodal | Vision | Integrate vision model | T-MULTI-001 |
| FR-014 | App shall support Audio Scribe (transcription/translation) on-device. | P1 | Multimodal | Audio | Integrate audio model | T-MULTI-002 |
| FR-015 | App shall expose personality and learning loop (SOUL.md, Honcho dialectic). | P2 | Agent | Personality | Design SOUL.md schema | T-AGENT-001 |
| FR-016 | App shall support cron automations and background tasks. | P2 | Agent | Crons | Implement scheduler | T-AGENT-002 |
| FR-017 | App shall allow custom model loading by users. | P1 | On-Device Inference | Custom Models | Add GGUF import flow | T-INFER-004 |
| FR-018 | App shall support Telegram/Discord/WhatsApp gateway for remote access. | P2 | Messaging | Platform Gateways | Add bot integrations | T-MSG-001 |

### Non-Functional Requirements (NFR)

| ID | Requirement | Priority | Task |
|----|-------------|----------|------|
| NFR-001 | On-device inference latency ≤ 20 tokens/sec on mid-range Android. | P0 | T-PERF-001 |
| NFR-002 | Memory footprint during inference ≤ 100 MB RAM. | P0 | T-PERF-002 |
| NFR-003 | 1,000 memory entries consume ≤ 25 MB disk. | P0 | T-PERF-003 |
| NFR-004 | App shall function fully offline for core features. | P0 | T-RES-001 |
| NFR-005 | App startup time ≤ 3 seconds on cold start. | P1 | T-PERF-004 |
| NFR-006 | APK size ≤ 150 MB (excluding downloaded models). | P1 | T-PERF-005 |
| NFR-007 | Cross-platform UI parity (Android/iOS/Desktop). | P1 | T-UX-001 |

### Security Requirements (SR)

| ID | Requirement | Priority | Task |
|----|-------------|----------|------|
| SR-001 | All artifacts signed with Ed25519. | P0 | T-SEC-001 |
| SR-002 | Zero Trust enforced: no implicit trust between components. | P0 | T-SEC-002 |
| SR-003 | RBAC with least privilege for all agents/tools. | P0 | T-SEC-003 |
| SR-004 | Policy-as-Code (OPA) mandatory for control-plane decisions. | P0 | T-SEC-004 |
| SR-005 | Secrets encrypted at rest and in transit. | P0 | T-SEC-005 |
| SR-006 | SBOM generated for every release. | P0 | T-SEC-006 |
| SR-007 | Dependency signature validation mandatory. | P0 | T-SEC-007 |
| SR-008 | Runtime attestation before privileged actions. | P1 | T-SEC-008 |
| SR-009 | User approval gate for sensitive device actions. | P0 | T-SEC-009 |
| SR-010 | Encrypted cross-device sync (E2E). | P1 | T-SEC-010 |

### Compliance Requirements (CR)

| ID | Requirement | Priority | Task |
|----|-------------|----------|------|
| CR-001 | GDPR data minimization: process only necessary data. | P1 | T-COMP-001 |
| CR-002 | Right to erasure: user can delete all local memory. | P1 | T-COMP-002 |
| CR-003 | Data residency controls per region. | P2 | T-COMP-003 |

### Performance Requirements (PR)

| ID | Requirement | Priority | Task |
|----|-------------|----------|------|
| PR-001 | Local LLM P95 latency < 500 ms for 512-token generation. | P0 | T-PERF-006 |
| PR-002 | RAG retrieval latency < 100 ms for 1,000 memories. | P0 | T-PERF-007 |
| PR-003 | Cloud fallback latency < 200 ms (network + model). | P1 | T-PERF-008 |

### Operational Requirements (OR)

| ID | Requirement | Priority | Task |
|----|-------------|----------|------|
| OR-001 | Canary deployment mandatory for production releases. | P0 | T-OPS-001 |
| OR-002 | Human approval gates at Architecture, Implementation, ProductionRelease. | P0 | T-OPS-002 |
| OR-003 | Error budget 0.01% with 99.99% availability target. | P1 | T-OPS-003 |
| OR-004 | Automated rollback if canary error rate > threshold. | P1 | T-OPS-004 |
| OR-005 | Multi-region failover respecting data residency. | P2 | T-OPS-005 |

## Epics
1. **EPIC-01: On-Device Inference** — Local LLM runtime, model registry, quantization.
2. **EPIC-02: Memory & RAG** — CozoDB, embeddings, retrieval.
3. **EPIC-03: Task Routing** — Local vs cloud decision engine.
4. **EPIC-04: Skill System** — Hermes sync, skill parser, evolution.
5. **EPIC-05: File Analysis** — Import, preprocess, debug, patch.
6. **EPIC-06: Multimodal** — Vision, audio, on-device preprocessing.
7. **EPIC-07: Security & Governance** — Zero Trust, OPA, RBAC, signing.
8. **EPIC-08: Platform & Messaging** — Android/iOS/Desktop, Telegram/Discord.

## Traceability Summary
- All P0 requirements map to Test IDs starting with T-.
- Each Feature maps to ≥1 Task and ≥1 Test.
- Security and Compliance requirements trace to ADR-005 (Security) and ADR-006 (Compliance).

---
[AEOS_STATUS: REQUIREMENTS_EXTRACTED]
