# 🧠 DeepEyeLLM — Edge-Native AI Workstation

<div align="center">

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android%2014%2B%20(ARM64)-brightgreen.svg)](https://developer.android.com)
[![Engine](https://img.shields.io/badge/Engine-llama.cpp%20Native%20JNI-orange.svg)](https://github.com/ggerganov/llama.cpp)
[![GPU](https://img.shields.io/badge/Acceleration-Vulkan%20%7C%20ARM%20NEON%20%7C%20OpenCL-purple.svg)](https://www.vulkan.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material3-cyan.svg)](https://developer.android.com/jetpack/compose)

**100% Private, On-Device AI Workstation with Real-Time Thought Deconstruction, Air-Gapped Crypto Sentinel, GBNF Prompt Lab, and Resumable Model Downloads.**

[Key Features](#-key-architectural-features) • [Quick Start](#-quick-start--build) • [Architecture](#-system-architecture) • [Supported Models](#-supported-models) • [Contributing](#-contributing) • [License](#-license)

</div>

---

## 🌟 Highlights

DeepEyeLLM transforms modern Android devices into private, autonomous AI workstations capable of running multi-billion parameter LLMs directly on hardware without cloud dependencies, API subscriptions, or data leakage.

```
                    ┌─────────────────────────────────────────┐
                    │       DeepEyeLLM Holographic UI         │
                    │   (Jetpack Compose + Cyber Aesthetics)  │
                    └────────────────────┬────────────────────┘
                                         │
                    ┌────────────────────▼────────────────────┐
                    │      EngineController & Orchestrator     │
                    │ (Kotlin Coroutines + StateFlow + Flow)  │
                    └─────────┬─────────────────────┬─────────┘
                              │                     │
      ┌───────────────────────▼───────┐   ┌─────────▼───────────────────────┐
      │  Resumable Downloader & Repo  │   │     JNI Native C++ Bridge       │
      │   (WorkManager + OkHttp HTTP) │   │ (llama-bridge.cpp + llama.cpp)  │
      └───────────────────────────────┘   └─────────┬───────────────────────┘
                                                    │
                                  ┌─────────────────▼─────────────────┐
                                  │   Hardware Compute Acceleration   │
                                  │  • Vulkan Compute Shader GPU      │
                                  │  • ARMv8.2-A+ DotProd / FP16 NEON │
                                  │  • Linux kernel mmap zero-copy    │
                                  └───────────────────────────────────┘
```

---

## ⚡ Key Architectural Features

### 🚀 1. Zero-Latency On-Device Inference
- **Native C++ JNI Core**: Powered by embedded `llama.cpp` with Vulkan GPU layer offloading and auto-fallback to multi-core CPU NEON.
- **Kernel mmap Zero-Copy Paging**: Loads 3GB+ model weights in seconds using Linux virtual memory mapping.
- **Flash Attention & Fused Kernels**: Native support for autoregressive KV cache, sliding window attention, and quantized KV heads.

### 📥 2. Resumable Chunked Model Downloads
- **Background WorkManager Engine**: Background downloads persist across app termination and device restarts.
- **OkHttp Range Header Streams**: Resumes broken downloads seamlessly without re-downloading existing chunks.
- **SHA-256 Checksum Verification**: Cryptographically validates file integrity before registering models into the engine.

### 📡 3. Dynamic Model Catalog & Caching
- **Automated Catalog Discovery**: Fetches remote JSON model specifications with local disk caching.
- **Hardware RAM-Fit Evaluator**: Automatically assesses device RAM and categorizes models into `Perfect Fit`, `Moderate Load`, or `OOM Warning`.
- **Custom Local Import**: 1-tap import for any `.gguf` or `.bin` model file from device storage or SD card.

### 🧠 4. Holographic Chat & Real-Time Thought Deconstruction
- **Live Stream Deconstruction**: Automatically separates `<think>` / `<thought>` reasoning blocks from final responses into collapsible holographic accordions.
- **Dynamic Telemetry HUD**: Monitors Tokens/Second, Time to First Token (TTFT), Memory Headroom, and Thermal Governor states.

### 🛡️ 5. Air-Gapped Crypto Sentinel & Non-Custodial DEX
- **Smart Contract Security Scoring**: Analyzes Solidity contracts for honeypots, liquidity locks, and malicious mint functions.
- **Simulated Intent Gates**: Non-custodial transaction preview and risk assessment prior to execution.
- **Zero-Trust Network Air-Gap**: One-tap toggle to sever WAN egress while maintaining local model execution.

### 🎛️ 6. GBNF Prompt Lab & Benchmark Suite
- **Side-by-Side A/B Prompt Testing**: Compare prompt outputs with independent temperature, Top-P, and system prompt configurations.
- **GBNF Grammar Constraints**: Force LLM outputs into strict JSON schemas, regular expressions, or structured intents.
- **Hardware Canvas Speedometer**: Benchmark raw token generation speed and export results to CSV.

---

## 📱 Supported Models

DeepEyeLLM supports all modern GGUF (V3) quantization formats (`Q4_K_M`, `Q4_K_S`, `Q5_K_M`, `Q8_0`, `IQ4_NL`, etc.):

| Model Family | Recommended Quant | Parameters | RAM Required | Architecture |
| :--- | :--- | :--- | :--- | :--- |
| **Gemma 2 / Gemma 4** | `Q4_K_M` | 2B / 9B | 2.0 GB - 5.5 GB | `gemma2` |
| **Hermes 3** | `Q4_K_M` | 3B / 8B | 2.2 GB - 5.2 GB | `llama` |
| **Llama 3.1 / 3.2** | `Q4_K_M` | 1B / 3B / 8B | 1.2 GB - 5.5 GB | `llama` |
| **Qwen 2.5 / Qwen 3** | `Q4_K_M` | 1.5B / 3B / 7B | 1.6 GB - 4.8 GB | `qwen2` |
| **DeepSeek R1 / V3** | `Q4_K_M` | Distill 1.5B / 7B | 1.6 GB - 4.8 GB | `qwen2` / `llama` |
| **Phi-3.5 / Phi-4** | `Q4_K_M` | 3.8B | 2.5 GB | `phi3` |

---

## 🛠️ Quick Start & Build

### Prerequisites
1. **JDK 17+** (`openjdk@17` or Oracle JDK)
2. **Android SDK 34+** (API 34 / Android 14)
3. **Android NDK 26+** (C++20 support for CMake / `llama.cpp`)
4. **Physical Android Device** with ARM64-v8a processor (6GB+ RAM recommended for 3B+ models)

### 1. Clone Repository
```bash
git clone https://github.com/DeepEyeCrypto/DeepEyeLLM.git
cd DeepEyeLLM
```

### 2. Configure Local Properties
Create `android/local.properties` with your Android SDK & NDK paths:
```properties
sdk.dir=/Users/your-username/Library/Android/sdk
ndk.dir=/Users/your-username/Library/Android/sdk/ndk/26.1.10909125
```

### 3. Build & Assemble Debug APK
```bash
cd android
./gradlew assembleDebug
```

### 4. Deploy to Connected Device via ADB
```bash
adb install -r -d -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.deepeye.agent/.MainActivity
```

---

## 📁 Repository Structure

```
DeepEyeLLM/
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── cpp/                    # Native C++ JNI Subsystem
│   │   │   │   ├── llama-bridge.cpp    # JNI Bridge for llama.cpp & Vulkan
│   │   │   │   ├── CMakeLists.txt      # Native build configuration
│   │   │   │   └── llama.cpp/          # High-performance upstream inference engine
│   │   │   ├── kotlin/com/deepeye/agent/
│   │   │   │   ├── core/               # Theme, hardware metrics, memory inspector
│   │   │   │   ├── di/                 # Dagger-Hilt dependency injection modules
│   │   │   │   ├── domain/             # EngineController, ModelRegistry, LocalModel
│   │   │   │   │   └── engine/         # LlamaCppEngine & LiteRTEngine implementations
│   │   │   │   ├── features/           # Crypto Sentinel, Skills, RAG, Web3 DEX
│   │   │   │   └── ui/                 # Jetpack Compose Cyber UI (Chat, Lab, Settings)
│   │   │   └── res/                    # Vector drawables, themes, and layouts
│   └── gradle/                         # Gradle build wrappers & scripts
├── CONTRIBUTING.md                     # Contribution guidelines
├── SECURITY.md                         # Security policy & reporting
├── LICENSE                             # Apache License 2.0
└── README.md                           # Documentation
```

---

## 🔒 Security & Privacy Posture

DeepEyeLLM operates on an **Air-Gapped Zero-Trust Foundation**:
- **Zero Telemetry Collection**: No analytics trackers, no crash reporters sending data to third parties.
- **Hardware Isolation**: Cryptographic seeds and private keys remain sealed inside Android KeyStore / StrongBox.
- **Air-Gapped Mode**: Software-defined kill switch for all outbound network traffic.

---

## 🤝 Contributing

Contributions from the open-source and edge AI community are warmly welcomed!
- Please read our [**CONTRIBUTING.md**](CONTRIBUTING.md) guide before submitting pull requests.
- Report security concerns confidentially via [**SECURITY.md**](SECURITY.md).

---

## 📄 License

DeepEyeLLM is released under the **[Apache License 2.0](LICENSE)**.

```
Copyright 2026 DeepEye Crypto & DeepEyeLLM Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
