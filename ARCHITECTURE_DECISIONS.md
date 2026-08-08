# ARCHITECTURE_DECISIONS.md

## ADR-001: Hybrid On-Device + Cloud Architecture

### Status
Accepted

### Context
The user demands a privacy-first AI agent that works offline but can escalate complex tasks to the cloud. Two primary reference architectures exist:
1. Google AI Edge Gallery (100% on-device, no learning loop).
2. Hermes Agent (cloud-only autonomous agent with learning loop).

Neither alone satisfies the requirement. A hybrid approach is necessary but introduces plane isolation, sync, and security challenges.

### Decision
Implement a **dual-plane architecture**:
- **Control Plane**: Isolated; handles policy, routing, identity, and governance. Runs locally and in cloud.
- **Data Plane**: Isolated; handles inference, memory, file I/O, and tool execution. Runs primarily on-device.
- **Cross-Plane Access**: Gated by OPA policies with explicit allowlists and audit logging.

### Tradeoff Analysis
| Option | Pros | Cons |
|--------|------|------|
| Pure on-device | Maximum privacy, zero cloud cost | Limited model size, no deep research, no skill evolution |
| Pure cloud | Best models, full agent capabilities | No offline, privacy risk, ongoing cost |
| Hybrid (chosen) | Offline core + cloud augmentation | Complexity, sync overhead, security surface |

### Cost Analysis
- On-device: Model download cost (one-time), no recurring inference cost.
- Cloud: ~$5–$50/month VPS or serverless; scales with usage.
- Routing reduces cloud cost by ~60% by keeping simple tasks local.

### Scalability
- On-device scales with hardware (NPU/GPU adoption).
- Cloud scales horizontally via serverless (Modal/Daytona).

### Reliability
- Offline fallback guarantees core functionality during outages.
- Cloud redundancy via multi-region failover.

### Maintainability
- Shared Rust core reduces duplication.
- Clear contracts between planes simplify testing.

### Security
- Plane isolation limits blast radius.
- OPA gating prevents unauthorized data exfiltration.

---

## ADR-002: LiteRT + MNN as On-Device Runtime

### Status
Accepted

### Context
Android and iOS require hardware-accelerated inference. Google's LiteRT supports Gemma and TFLite models well; MNN supports broader GGUF/ONNX ecosystem.

### Decision
- **Primary**: LiteRT for Gemma family and Google AI Edge optimized models.
- **Secondary**: MNN for Qwen, Llama, Phi GGUF models and custom user imports.

### Tradeoff Analysis
| Runtime | Gemma Support | GGUF Support | Hardware Acceleration | Size |
|---------|-------------|------------|----------------------|------|
| LiteRT | Excellent | Poor | GPU/NPU via delegates | ~15 MB |
| MNN | Good | Excellent | GPU/CPU | ~12 MB |
| llama.cpp | Good | Excellent | CPU/GPU (vulkan) | ~5 MB |

LiteRT + MNN dual runtime chosen to cover both Google-optimized and community model ecosystems without llama.cpp's mobile integration complexity.

---

## ADR-003: CozoDB for Local Memory

### Status
Accepted

### Context
Need embedded DB supporting vectors, full-text search, and graph relations under strict size constraints.

### Decision
CozoDB (Rust, Datalog-like, 4 MB lib) with on-device embedding model (e.g., quantized all-MiniLM or multilingual equivalent).

### Tradeoff Analysis
| DB | Size | Vectors | FTS | Graph | Rust FFI |
|----|------|---------|-----|-------|----------|
| SQLite + sqlite-vec | ~2 MB | Yes | Yes | Easy |
| CozoDB | ~4 MB | Yes | Yes | Yes | Medium |
| Chroma (remote) | N/A | Yes | Yes | No | N/A |

CozoDB chosen for graph capabilities (dependency tracking, skill relationships) at acceptable size cost.

---

## ADR-004: Hermes Agent as Cloud Skill Backend

### Status
Accepted

### Context
Hermes Agent provides the only known open-source self-improving agent with skill evolution (DSPy + GEPA). User wants skills to auto-update.

### Decision
Run Hermes Agent on cloud VM/serverless. Expose:
- `/skills/sync` — JSON feed of available skills.
- `/skills/run` — Execute skill on uploaded files/code.
- `/analysis/deep-debug` — Multi-tool debugging workflow.

### Tradeoff Analysis
| Approach | Auto-Sync | Deep Debug | Cost | Privacy |
|----------|-----------|------------|------|---------|
| Hermes cloud (chosen) | Yes | Yes | Medium | Data encrypted in transit |
| Port Hermes to mobile | Hard | Limited | Zero | Best but impractical |
| Reimplement skills | Slow | Medium | Low | Good but misses upstream updates |

---

## ADR-005: OPA for Policy-as-Code

### Status
Accepted

### Context
Enterprise governance demands mandatory, auditable, versioned policy enforcement.

### Decision
Open Policy Agent (OPA) sidecar/policy service evaluating Rego policies for:
- Task routing (local vs cloud)
- Tool access (which tools can run)
- Model selection (which model for which data class)
- Data exfiltration prevention

### Tradeoff Analysis
| Engine | Maturity | Performance | Learning Curve | Mobile Fit |
|--------|----------|-------------|----------------|------------|
| OPA | High | Fast | Medium | Server-side |
| Cedar | Medium | Fast | Low | AWS-centric |
| Custom Kotlin rules | Low | Medium | Low | Mobile-native |

OPA chosen for maturity and ecosystem; mobile uses lightweight cached policy snapshot for offline enforcement.

---

## ADR-006: Ed25519 Artifact Signing + SBOM

### Status
Accepted

### Context
Supply chain attacks and provenance requirements mandate signing and SBOMs.

### Decision
- Sign APK/IPA/DMG/EXE with Ed25519.
- Generate SBOM (CycloneDX) at build time.
- Publish signatures and SBOMs alongside releases.

### Tools
- Signing: `minisign` or `sigstore` cosign.
- SBOM: `syft` for container/app analysis.

---

## ADR-007: Tauri v2 for Desktop Shell

### Status
Accepted

### Context
User prefers Tauri v2 (Rust + WebView) over Electron for desktop.

### Decision
Tauri v2 desktop app reusing shared Rust core (`shared/` crate) with IPC isolate for security.

### Tradeoff Analysis
| Framework | Size | Security | Rust Core Reuse | Mobile |
|-----------|------|----------|---------------|--------|
| Tauri v2 | ~5 MB | Strong (IPC isolate) | Excellent | No |
| Electron | ~150 MB | Medium | Poor | No |
| Flutter Desktop | ~30 MB | Medium | Requires FFI | Yes (shares code) |

Tauri chosen for security and size; Flutter considered for future mobile-desktop unification.

---

## ADR-008: STRIDE + FAIR for Risk Framework

### Status
Accepted

### Context
Need consistent threat modeling and risk quantification.

### Decision
STRIDE for threat modeling; FAIR for quantitative risk scoring.

### Consequences
Requires training/ tooling; yields defensible risk posture.

---

## ADR-009: Multi-LLM Registry with Routing

### Status
Accepted

### Context
Support Gemma, Qwen, Llama, Phi locally; multiple providers remotely.

### Decision
ModelRegistry abstraction with task-type → model mapping; router decides local vs remote.

### Consequences
Adds configuration surface; maximizes flexibility.

---

## ADR-010: Skill Auto-Sync from Upstream Hermes

### Status
Accepted

### Context
Skills update hone chahiye automatically.

### Decision
Background sync job polls Hermes `skills/` dir; converts SKILL.md to JSON; stores in CozoDB.

### Consequences
Upstream breaking changes can destabilize app; need version pinning and rollback.
