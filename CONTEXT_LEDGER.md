# CONTEXT_LEDGER

## Project Identity
- **Project Name**: DeepEyeAgent (tentative; derived from user background)
- **System ID**: AEOS_v2026.12.16_OMEGA
- **Context Source**: Full visible conversation history (user + agent)
- **Extraction Date**: 2026-06-03

## Goals
1. Build a hybrid on-device + cloud autonomous AI agent platform.
2. Combine Google AI Edge Gallery (offline on-device LLM) with Hermes Agent (autonomous learning loop).
3. Enable auto-updating skills when Hermes upstream evolves.
4. Support multiple LLM models both locally and remotely.
5. Allow analysis and debugging of any file type (code, logs, binaries, images, audio).
6. Maintain privacy-first architecture: sensitive data never leaves the device unless user explicitly opts in.
7. Support Android (primary), iOS, Linux (Kali/Parrot), and desktop (Tauri v2).
8. Operate under enterprise-grade governance (Zero Trust, Policy-as-Code, Formal Verification, STRIDE/FAIR).

## Features
- On-device LLM inference (Gemma 4, Qwen 2.5, Llama, Phi-3, etc.) via LiteRT/MNN.
- Local memory engine (CozoDB/memlocal) for cross-session recall and RAG.
- Task router: decides local vs cloud execution based on complexity, privacy, cost.
- Auto-sync skill catalog from Hermes Agent upstream (`NousResearch/hermes-agent`).
- Hermes self-evolution integration (DSPy + GEPA) for skill optimization.
- Mobile Actions: device control via FunctionGemma 270m (Android intents, settings, apps).
- Multimodal on-device: Ask Image (vision/OCR), Audio Scribe (transcription/translation).
- File analysis pipeline: local RAG + cloud deep debug via Hermes tools.
- Policy-as-Code engine (OPA) for cross-plane access control.
- Ed25519 artifact signing and SBOM generation.
- Multi-region sovereign orchestration with data residency controls.

## Constraints
- User is an Android developer and security researcher (deep knowledge of APKs, ELF, static analysis).
- Primary dev machine: Kali/Parrot Linux.
- Cross-platform UI: Tauri v2 (Rust + WebView).
- Must be fully open source (Apache-2.0 + MIT dual license expected).
- Mobile hardware limits: quantized models (2B–4B primary, up to 7B on flagship).
- Cloud fallback is optional; core value is offline-first.
- No cloud vector DB for sensitive memory; all embeddings stay local.

## Tech Preferences
- **Mobile**: Kotlin (Android), Swift (iOS)
- **Core runtime**: Rust (shared library, CozoDB, Tauri IPC isolate)
- **Cloud bridge**: Python (FastAPI/Flask) wrapping Hermes Agent
- **On-device inference**: LiteRT (Google) or MNN (Alibaba)
- **Memory**: CozoDB (embedded, 4 MB Rust lib)
- **Policy engine**: Open Policy Agent (OPA)
- **Container/VM**: Daytona, Modal, or self-hosted VPS
- **Signing**: Ed25519
- **Threat modeling**: STRIDE
- **Risk quantification**: FAIR

## Security Expectations
- Zero Trust Architecture enforced.
- Least privilege for all agents, tools, and services.
- RBAC with approval-override roles (SecurityOfficer, ReleaseManager).
- Continuous runtime attestation and identity revalidation.
- Supply chain security: dependency signature validation + SBOM.
- AI safety: red teaming, prompt injection benchmarks, hallucination benchmarks.
- Encrypted cross-device sync (optional cloud feature).

## Business Requirements
- Open source project with community contributions.
- Skill marketplace for community-shared reusable workflows.
- Resource credit system for cloud usage (budget marketplace).
- Enterprise readiness for future B2B licensing.

## User Priorities (Ranked)
1. Privacy / offline-first operation.
2. File analysis and debugging capabilities (security research use case).
3. Auto-updating skills from Hermes upstream.
4. Multi-LLM support (local + cloud).
5. Enterprise governance and compliance.

## Confidence Report
| Item | Confidence | Notes |
|------|-----------|-------|
| Product vision | High | Clear from repeated user queries. |
| Tech stack | High | Explicitly stated preferences. |
| Security model | High | Detailed governance config provided. |
| Performance expectations | Medium | No explicit SLOs given by user; inferred. |
| Business model | Low | Assumed open-source-first; no monetization details. |
| Compliance targets | Medium | No specific regulation named (GDPR/HIPAA/etc. inferred). |

## Missing Information Report
- No explicit target user personas (consumer vs enterprise vs security pro).
- No specific Android API level floor beyond "Android 12+" inferred from Edge Gallery.
- No cloud provider preference (GCP/AWS/Azure).
- No explicit monetization strategy.
- No named regulatory framework (GDPR, CCPA, SOC2, etc.).
- No performance benchmarks for on-device inference speed (tokens/sec target).

## Gap Analysis
1. **Compliance framework**: Need to select specific regulation (GDPR default assumed).
2. **Cost model**: Cloud resource pricing not defined; need showback/chargeback design.
3. **iOS specifics**: Apple Intelligence / CoreML / App Intents integration details sparse.
4. **Desktop parity**: Tauri v2 integration plan not deeply explored.
5. **Testing hardware**: No target device list (Pixel, Samsung, Xiaomi, etc.).

---
[AEOS_STATUS: CONTEXT_INGESTED]
