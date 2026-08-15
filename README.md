# 🧠 DeepEyeLLM — Edge-Native AI Workstation

> **100% Private, On-Device AI Workstation with Real-Time Thinking Streams, Visual DAG Agent Workflows, Air-Gapped Crypto Sentinel, and Agent Skills Standard (Mobile-First with Adaptive Tablet/Desktop Shell).**

---

## ⚡ Key Architectural Innovations

- **Obsidian Bento Command Center (`WorkstationHomeScreen.kt`)**: Industrial cyber-tactile UI with live memory headroom, thermal throttling indicator, and quick-launch module routing.
- **Thinking Mode & Reasoning Engine (`ThinkingAccordion.kt`)**: Real-time `<think>` / `<thought>` stream separation, millisecond step metrics, and animated expansion.
- **Inline Ephemeral Tool Cards (`ToolExecutionCard.kt`)**: Millisecond execution timers with Human-in-the-Loop approval gates (`Allow Action` / `Deny`).
- **Prompt Lab A/B Testing Studio (`PromptLabScreen.kt`)**: Side-by-side prompt testing canvas with independent Temperature/Top-P controls and GBNF Grammar Builder (`Strict JSON`, `Contract Audit`, `Tool Intent`).
- **Edge LLM Benchmark Suite (`BenchmarkScreen.kt`, `TelemetrySpeedometer.kt`)**: Hardware-accelerated Canvas speedometer measuring Tokens/sec, TTFT, and Peak RAM usage with CSV export.
- **Agent Skills Standard Manifest Store (`SkillStoreScreen.kt`, `SkillRegistry.kt`)**: `SKILL.md` manifest specification with verification gates (`Liquidity Lock >= 90d`, `Zero-Regression Syntax Check`), tool capability declarations, and anti-rationalization constraints.
- **Transparent Working Context Ledger (`MemoryInspectorView.kt`, `KnowledgeBaseScreen.kt`)**: Real-time context token usage progress bar, KV-cache allocation gauge, and episodic memory CRUD.
- **Air-Gapped Crypto Sentinel & DEX Browser (`CryptoSentinelCard.kt`, `BraveBrowserScreen.kt`)**: On-chain security scorecard (0–100), Liquidity Lock & Honeypot checks, non-custodial simulation intent gates, and embedded sliding audit drawer.
- **Visual DAG Agent Studio (`VisualDagExecutionCard.kt`, `AgentStudioScreen.kt`)**: 5-step DAG workflow tree with animated node states, per-step timing, and token telemetry.
- **Radar-Discovered P2P Mesh (`RadarDiscoveryCanvas.kt`, `P2PShareScreen.kt`)**: Hardware-accelerated 360° concentric radar sweep, Ed25519 peer verification, and chunked model sharding.
- **Zero-Trust Security & RBAC Guard (`SecurityDashboardScreen.kt`)**: Canary token integrity monitor, strict air-gapped network egress switch, and cryptographic audit log ledger with SHA-256 hashes.
- **Hardware Memory-Fit Evaluator & Thermal Governor (`HardwareFitEvaluator.kt`)**: Dynamic RAM fit calculation (`Perfect RAM Fit`, `Moderate Load`, `OOM Warning`), thermal thread governor, and Zero-Blur Performance Mode.

---

## 🛠️ Build & Compilation

### Prerequisites
- JDK 17+
- Android SDK 34+
- Android NDK (for native Vulkan / OpenCL / QNN backends)

### Compile Universal APK (All Backends Included)
```bash
cd android

# Build Universal APK (Vulkan GPU + LiteRT NPU + ARM NEON)
./gradlew assembleDebug

# Install Directly to Connected Android Device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## 🔒 Security & Privacy Model

DeepEyeLLM is built from the ground up on a **Zero-Trust & Air-Gapped Trust Model**:
1. **100% On-Device Inference**: Models run locally via LiteRT NPU, Vulkan, OpenCL, or CPU NEON.
2. **Strict Air-Gapping**: Network egress is switchable to block all WAN sockets; only localhost RPCs and verified BLE/Wi-Fi mesh peer syncs are permitted.
3. **Non-Custodial Crypto Intent Gates**: User private keys never leave local hardware keystores; all DEX actions are simulated prior to execution.
4. **Verifiable Audit Ledger**: Every action taken by autonomous agents is logged with a cryptographic SHA-256 decision hash.

---

## 📄 License
Apache 2.0 / DeepEye Open Source.
