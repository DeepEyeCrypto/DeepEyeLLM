# DeepEyeLLM - Build & Ship Guide for Hardware Acceleration

This document provides full instructions for building, installing, and validating hardware-accelerated GGUF LLM inference on physical Android devices.

---

## 1. Build Variant Selection & Commands

DeepEyeLLM supports three specialized hardware acceleration build variants:

### A. Vulkan GPU Variant (Recommended for Qualcomm Adreno & ARM Mali GPUs)
```bash
./android/gradlew -p android assembleVulkanDebug
```
*APK Location:* `android/app/build/outputs/apk/vulkan/debug/app-vulkan-debug.apk`

### B. OpenCL GPU Variant (Fallback for legacy Mali / Adreno devices)
```bash
./android/gradlew -p android assembleOpenclDebug
```
*APK Location:* `android/app/build/outputs/apk/opencl/debug/app-opencl-debug.apk`

### C. Hexagon QNN NPU Variant (Qualcomm Snapdragon NPU Acceleration)
```bash
./android/gradlew -p android assembleQnnDebug
```
*APK Location:* `android/app/build/outputs/apk/qnn/debug/app-qnn-debug.apk`

---

## 2. ADB Installation & Launch

Install the target variant onto a connected device via ADB:

```bash
# Install Vulkan GPU Variant
adb install -r android/app/build/outputs/apk/vulkan/debug/app-vulkan-debug.apk

# Launch App
adb shell am start -n com.deepeye.agent/.MainActivity
```

---

## 3. Logcat Monitoring & Performance Filtering

Observe native JNI initialization, hardware backend selection, and per-token generation metrics:

```bash
# View JNI & Native performance logs
adb logcat -s DeepEyeLLM-Native | grep "Performance:"

# Automated execution script (build + install + test + logcat)
./test_backends.sh
```

---

## 4. Real-Device Smoke Test Checklist

- [ ] **Catalog & Model Download**: Open Settings → Manage Models. Download a GGUF model (e.g. Qwen2.5-0.5B / Llama-3.2-1B). Verify pause, resume, and SHA-256 checksum verification.
- [ ] **Backend Activation**: Confirm backend selection in logcat (`[DeepEyeLLM-Native] Backend initialized: Vulkan / QNN / OpenCL`).
- [ ] **Streaming Chat**: Send a message in Chat. Verify real-time token streaming with typing cursor pulse (`▌`), cancel generation button, and TTFT under 300ms.
- [ ] **Benchmarking Suite**: Navigate to Benchmark tab, click **Start On-Device Benchmark Suite**, verify live progress and TTFT/tok-s results table. Export CSV report to `/Download/DeepEyeLLM/`.
