# =============================================================================
# CMakeLists.txt — DeepEyeLLM Native JNI Bridge + llama.cpp & Colibri Engines
# =============================================================================
# Submodules / subdirectories:
#   - llama.cpp: GGUF LLM inference engine (arm64 + Vulkan)
#   - colibri: High-performance MoE / transformer inference engine (CPU/Edge)
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)
project("deepeye_native" C CXX)

list(APPEND CMAKE_PREFIX_PATH "/usr/local" "/usr/local/share/cmake" "/opt/homebrew")
include_directories("/usr/local/include")

# Optimization flags for arm64-v8a / mobile inference
add_compile_options(
    -isystem/usr/local/include
    -O3
    -DNDEBUG
    -fno-finite-math-only
    -march=armv8.2-a+dotprod+fp16
    -fvectorize
    -funroll-loops
    -finline-functions
    -fomit-frame-pointer
)

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
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    add_subdirectory(llama.cpp)
endif()

# ── Android system libraries ────────────────────────────────────────────────
find_library(log-lib log)
find_library(vulkan-lib vulkan)

# ── llama_jni shared library ────────────────────────────────────────────────
if(TARGET llama)
    add_library(llama_jni SHARED llama-bridge.cpp)

    target_include_directories(llama_jni PRIVATE
        llama.cpp/include            # llama.h
        llama.cpp/ggml/include       # ggml.h
    )

    target_link_libraries(llama_jni
        llama                       # Core llama.cpp library
        ${log-lib}                  # Android logging
        ${vulkan-lib}               # Android Vulkan GPU driver library
    )
endif()

# ── Colibri Inference Engine ────────────────────────────────────────────────
set(COLIBRI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/colibri/c")

if(EXISTS "${COLIBRI_DIR}/edge_runtime.c")
    # Colibri Core C sources
    set(COLIBRI_SRCS
        ${COLIBRI_DIR}/edge_runtime.c
        ${COLIBRI_DIR}/segment_runtime.c
        ${COLIBRI_DIR}/expert_store_registry.c
        ${COLIBRI_DIR}/colibri.c
        ${COLIBRI_DIR}/glm53.c
        ${COLIBRI_DIR}/inkling.c
        ${COLIBRI_DIR}/kimi_k3.c
        ${COLIBRI_DIR}/olmoe.c
        ${COLIBRI_DIR}/qwen36.c
        ${COLIBRI_DIR}/qwen38.c
    )

    # Per-source compiler definitions matching Colibri segment/edge architecture
    set_source_files_properties(
        ${COLIBRI_DIR}/colibri.c PROPERTIES
        COMPILE_DEFINITIONS "COLIBRI_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/glm53.c PROPERTIES
        COMPILE_DEFINITIONS "GLM53_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/inkling.c PROPERTIES
        COMPILE_DEFINITIONS "INKLING_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/kimi_k3.c PROPERTIES
        COMPILE_DEFINITIONS "KIMI_K3_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/olmoe.c PROPERTIES
        COMPILE_DEFINITIONS "OLMOE_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/qwen36.c PROPERTIES
        COMPILE_DEFINITIONS "QWEN36_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )
    set_source_files_properties(
        ${COLIBRI_DIR}/qwen38.c PROPERTIES
        COMPILE_DEFINITIONS "QWEN38_NO_MAIN=1;COLI_SEGMENT_ADAPTER=1;COLI_EDGE_ADAPTER=1"
    )

    # Static Colibri Engine Library
    add_library(colibri_static STATIC ${COLIBRI_SRCS})

    target_include_directories(colibri_static PUBLIC
        ${COLIBRI_DIR}
    )

    target_compile_definitions(colibri_static PRIVATE
        _GNU_SOURCE=1
        _FILE_OFFSET_BITS=64
    )

    # JNI Bridge for Colibri Engine
    add_library(colibri_jni SHARED
        colibri_jni_bridge.cpp
    )

    target_include_directories(colibri_jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${COLIBRI_DIR}
    )

    target_compile_definitions(colibri_jni PRIVATE
        _GNU_SOURCE=1
        _FILE_OFFSET_BITS=64
    )

    target_link_libraries(colibri_jni
        colibri_static
        ${log-lib}
        m
    )
endif()

