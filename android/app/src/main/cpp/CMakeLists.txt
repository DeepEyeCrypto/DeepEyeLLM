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
option(GGML_VULKAN "Enable Vulkan GPU backend" OFF)
option(GGML_OPENCL "Enable OpenCL GPU backend" OFF)
option(GGML_QNN    "Enable Hexagon QNN NPU backend" OFF)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)   # OpenMP not available on Android NDK

if(GGML_VULKAN)
    add_compile_definitions(GGML_USE_VULKAN=1)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    if(NOT Vulkan_GLSLC_EXECUTABLE)
        find_program(Vulkan_GLSLC_EXECUTABLE NAMES glslc HINTS "/usr/local/bin" "/usr/local/share/android-commandlinetools/ndk/27.0.12077973/shader-tools/darwin-x86_64")
    endif()
endif()

if(GGML_OPENCL)
    add_compile_definitions(GGML_USE_OPENCL=1)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
endif()

if(GGML_QNN)
    add_compile_definitions(GGML_USE_QNN=1)
    set(GGML_QNN ON CACHE BOOL "" FORCE)
    set(GGML_QNN_ENABLE_QNN_BACKEND ON CACHE BOOL "" FORCE)
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
