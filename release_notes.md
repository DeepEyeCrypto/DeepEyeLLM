# DeepEyeLLM v2.0.0

Production Release of DeepEyeLLM: Ultra-Low Latency Mobile AI Agent with Native GGUF JNI Engine.

### 🌟 Key Highlights
- **UI/UX Redesign**: Complete overhaul featuring Volumetric Glassmorphism, specular highlights, and calm, low-cognitive-load interfaces.
- **Adaptive Layouts**: Full support for phones, tablets, foldables, and desktop layouts via NavigationSuiteScaffold and split-pane architecture.
- **Native Inference Engine**: llama.cpp JNI integration supporting GGUF models with strict 2.1GB LMK safety cap.
- **Hardware Acceleration Fixes**: Improved Vulkan GPU layer offloading (with pre-compiled SPIR-V shaders support) and configurable thread count.
- **Autonomous AI Agent Studio**: 5-phase Deep Research loop engine with real-time UI tracking.
- **Zero-Latency UI Performance**: Offscreen compositing background rendering, memoized derived states, and stable keys on all LazyColumns.
- **Accessibility Enhancements**: WCAG 2.2 contrast compliance, screen-reader semantics, and automatic opacity fallback for 'Reduce Transparency'.

### 📦 Artifacts
- `app-vulkan-debug.apk`: Compiled Android APK ready for installation (Vulkan Accelerated).
