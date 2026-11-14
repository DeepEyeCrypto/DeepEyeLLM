// =============================================================================
// colibri_bridge.h — DeepEyeLLM JNI Bridge Header for Colibri Inference Engine
// =============================================================================
// JNI bridge that translates Kotlin ColibriEngine calls into native Colibri
// C-runtime API. Function declarations match the JNI signatures expected by
// the Kotlin ColibriEngine class.
// =============================================================================

#ifndef COLIBRI_BRIDGE_H
#define COLIBRI_BRIDGE_H

// JNI types are provided by the jni.h header included by the compiler via
// the Android NDK toolchain. Do not #include <jni.h> directly here — let the
// build system (-isystem or target_include_directories) make it available.

#ifdef __cplusplus
extern "C" {
#endif

// ---------------------------------------------------------------------------
// colibriInit — (Ljava/lang/String;Ljava/lang/String;II)J
//   model_dir  : Path to directory containing the Colibri model files
//   family     : Model family identifier ("qwen38", "qwen36", "glm53", "olmoe")
//                or nullptr/"auto" for auto-detection
//   max_ctx    : Maximum context tokens
//   threads    : Desired thread count
//   returns    : Opaque handle (jlong) to pass to other functions, or 0 on failure
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriInit(
    JNIEnv* env, jobject thiz,
    jstring jmodel_dir, jstring jfamily, jint max_ctx, jint threads);

// ---------------------------------------------------------------------------
// colibriLoadModel — (JLjava/lang/String;)I
//   handle     : Opaque handle from colibriInit
//   family     : Optional model family override (nullptr = use family from init)
//   returns    : Status code — COLIBRI_STATUS_OK on success, or one of:
//                  COLIBRI_STATUS_NOT_INITIALIZED, COLIBRI_STATUS_RUNTIME_ERROR
// ---------------------------------------------------------------------------
#define COLIBRI_STATUS_OK              0
#define COLIBRI_STATUS_NOT_INITIALIZED 1
#define COLIBRI_STATUS_RUNTIME_ERROR   2
JNIEXPORT jint JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriLoadModel(
    JNIEnv* env, jobject thiz, jlong handle, jstring jfamily);

// ---------------------------------------------------------------------------
// colibriGenerateSync — (JLjava/lang/String;FF)Ljava/lang/String;
//   Synchronous generation: blocks until max_tokens are generated (or EOS),
//   then returns the full completed text as a Java String.
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriGenerateSync(
    JNIEnv* env, jobject thiz, jlong handle,
    jstring jprompt, jint max_tokens, jfloat temperature, jfloat top_p);

// ---------------------------------------------------------------------------
// colibriFree — (J)V
//   handle : Opaque handle from colibriInit (or colibriLoadModel)
//   frees all native resources (edge engine, segment engine, session)
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriFree(
    JNIEnv* env, jobject thiz, jlong handle);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // COLIBRI_BRIDGE_H