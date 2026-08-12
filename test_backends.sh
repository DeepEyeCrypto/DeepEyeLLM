#!/usr/bin/env bash
# test_backends.sh

set -e

# Build all variant APKs
./android/gradlew -p android assembleVulkanDebug assembleOpenclDebug assembleQnnDebug

# Deploy Vulkan variant APK
adb install -r android/app/build/outputs/apk/vulkan/debug/app-vulkan-debug.apk

# Launch App Activity
adb shell am start -n com.deepeye.agent/.MainActivity

# Filter Native Logs & Performance Metrics
adb logcat -c
adb logcat -s DeepEyeLLM-Native | grep --line-buffered "Performance:"
