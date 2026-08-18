# Contributing to DeepEyeLLM

Thank you for your interest in contributing to **DeepEyeLLM**! We welcome contributions to help make edge-native AI inference faster, safer, and more accessible on mobile devices.

---

## 🛠️ Code of Conduct
We are committed to providing a friendly, safe, and welcoming environment for all contributors. Please be respectful and constructive in all discussions, issues, and pull requests.

---

## 🚀 How to Contribute

### 1. Reporting Bugs
- Search existing issues to ensure the bug hasn't already been reported.
- Create a new issue describing:
  - Device Model (e.g., Realme RMX3945, Pixel 8, Galaxy S24).
  - Android OS version & Architecture (`arm64-v8a`).
  - Active Model name & GGUF Quantization (`Q4_K_M`, `Q8_0`).
  - Steps to reproduce & Logcat output (`adb logcat -d | grep -E "DeepEye|DeepEyeLLM-Native|llama"`).

### 2. Submitting Pull Requests
1. **Fork the repository** and clone your fork.
2. **Create a topic branch**:
   ```bash
   git checkout -b feature/my-awesome-feature
   ```
3. **Write Clean, Idiomatic Code**:
   - Follow Kotlin official style guidelines.
   - For C++ changes in `llama-bridge.cpp` or `llama.cpp`, adhere to standard modern C++20 conventions.
   - Keep keystroke and input hot-paths zero-allocation and non-blocking.
4. **Test Your Changes**:
   - Ensure unit tests pass: `./gradlew testDebugUnitTest`
   - Test on a physical Android device or emulator.
5. **Commit with Conventional Commits**:
   - `feat(engine): add support for custom quantization format`
   - `fix(ui): prevent recomposition lag during high-token streaming`
   - `docs(readme): update supported model matrix`
6. **Submit a Pull Request** against the `main` branch.

---

## 🏗️ Architecture & Development Rules
- **Local Source of Truth**: All inferencing and state management must work 100% offline without remote server requirements.
- **Zero In-Keystroke Reactivity**: Avoid placing state writes or heavy computations on the UI hot path.
- **Memory Safety**: Check available device RAM before memory mapping large model weights.

Thank you for building the future of decentralized Edge AI!
