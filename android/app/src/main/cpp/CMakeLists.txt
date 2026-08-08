# =============================================================================
# CMakeLists.txt — DeepEyeLLM JNI Bridge + llama.cpp Integration
# =============================================================================
# Requires: llama.cpp source tree at ./llama.cpp (added via git submodule)
#   git submodule add https://github.com/ggml-org/llama.cpp.git \
#       android/app/src/main/cpp/llama.cpp
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)
project("llama_jni" CXX)

list(APPEND CMAKE_PREFIX_PATH "/usr/local" "/usr/local/share/cmake" "/opt/homebrew")
include_directories("/usr/local/include")
add_compile_options(-isystem/usr/local/include -O3 -fno-finite-math-only -march=armv8.2-a+dotprod+fp16)

# ── llama.cpp build options ──────────────────────────────────────────────────
# Disable everything we don't need to speed up the build
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)   # OpenMP not available on Android NDK
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)   # Uses ARM NEON CPU SIMD acceleration to prevent Adreno driver buffer crashes

# ── Vulkan GPU acceleration setup for Android NDK ──────────────────────────────
set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/darwin-x86_64/glslc" CACHE FILEPATH "" FORCE)

if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers INTERFACE)
    target_include_directories(SPIRV-Headers INTERFACE
        "${ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"
    )
    add_library(SPIRV-Headers::SPIRV-Headers ALIAS SPIRV-Headers)
    set(SPIRV-Headers_FOUND TRUE CACHE BOOL "" FORCE)
endif()

# ── Add llama.cpp source tree ────────────────────────────────────────────────
add_subdirectory(llama.cpp)

# ── Our JNI bridge shared library ────────────────────────────────────────────
add_library(llama_jni SHARED llama-bridge.cpp)

target_include_directories(llama_jni PRIVATE
    llama.cpp/include            # llama.h
    llama.cpp/ggml/include       # ggml.h
)

find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_link_libraries(llama_jni
    llama                       # Core llama.cpp library (includes ggml transitively)
    ${log-lib}                  # Android logging
    ${vulkan-lib}               # Android Vulkan GPU driver library
)
