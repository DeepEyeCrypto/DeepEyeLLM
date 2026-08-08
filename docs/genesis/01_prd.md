# 01 — Product Requirement Document (PRD): DeepEyeLLM

## Executive Summary
**DeepEyeLLM** is an enterprise-grade, local-first, zero-latency Android AI agent environment. It synthesizes four major upstream paradigms into a single unified mobile platform:
1. **Google AI Edge Gallery**: On-device LLM inference via Google LiteRT (FlatBuffer `.bin`) and `llama.cpp` (`.gguf`).
2. **Hermes Agent Framework**: Persistent agent memory mesh, episodic storage, and tool/skill dispatch pipelines.
3. **Roo Code Environment**: Mobile IDE workspace with visual diffs, AST file inspection, and automated code patching.
4. **Termux / proot-Ubuntu Backend**: Isolated local shell execution container for system diagnostics and local tooling.

---

## Target Audience
- **Senior Engineers & Security Researchers**: Requiring off-grid, zero-telemetry code audit and local LLM execution.
- **Indie Developers & Mobile Power Users**: Demanding editor-grade typing responsiveness and on-device multi-modal intelligence.

---

## Core Product Pillars

### 1. Multi-Engine Local Inference (Control/Data Isolation)
- **LiteRT Engine**: GPU/NPU accelerated inference for Gemma 2B/4B INT4 flatbuffers.
- **llama.cpp Engine**: CPU/ARM-DotProduct accelerated inference for GGUF models (Qwen 3, Llama 4, Phi-4).
- **Polymorphic Router**: Dynamic runtime selection based on model specification with memory-guard checks.

### 2. Cognitive Memory & Skill Engine (Hermes Alignment)
- **Episodic Memory**: User interactions and session context indexed locally via Room DB.
- **Skill Engine**: Pluggable tool registry for automated file analysis, code debugging, and system audits.

### 3. Mobile IDE & Code Patching (Roo Code Alignment)
- **Zero-Latency Typing Area**: Bypasses synthetic event loops to guarantee $\le 1\text{ms}$ keystroke handling.
- **Surgical Patch Engine**: Unified diff previewer and automated AST code editing.

### 4. Containerized Environment (Termux / proot Alignment)
- **Local Diagnostics**: Policy-gated execution of local tools, linting, and diagnostic scripts.
- **Air-Gapped Privacy**: 100% on-device operation; zero external telemetry or cloud dependencies.

---

## Explicit Boundaries & Non-Goals
- **No Cloud Fallbacks**: All inference and processing must execute strictly on-device.
- **No Keystroke IPC**: Keystrokes must never trigger high-volume IPC or un-throttled store writes.
- **No Ad-Hoc Styling**: All UI components must consume central design tokens from `05_design.md`.
