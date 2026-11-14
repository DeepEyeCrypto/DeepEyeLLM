// =============================================================================
// colibri_bridge.cpp — DeepEyeLLM JNI Bridge Implementation for Colibri Engine
// =============================================================================
// JNI bridge that translates Kotlin ColibriEngine calls into native Colibri
// C-runtime API calls. All functions follow the JNIEXPORT calling convention.
// The colibri_static library (already built in CMakeLists.txt) provides the
// Colibri C API functions (edge_runtime.h, segment_runtime.h).
// =============================================================================

#include "colibri_bridge.h"

// ---------------------------------------------------------------------------
// Colibri C-runtime types and forward declarations
// These types (ColibriContext, ColiEdgeEngine, etc.) are defined in the
// colibri static library that we link against. We forward-declare what we need.
// ---------------------------------------------------------------------------

#ifndef COLI_EDGE_DTYPE_INVALID
// Minimal forward declarations — full definitions come from the static library
typedef struct { int dummy; } ColiEdgeEngine;
typedef struct { int dummy; } ColiSegmentEngine;
typedef struct { int dummy; } ColiSegmentSession;
#endif

// ColibriContext holds all native state for a single engine instance
struct ColibriContext {
    std::string model_dir;
    std::string family;
    ColiEdgeEngine* edge = nullptr;
    ColiSegmentEngine* segment = nullptr;
    ColiSegmentSession* session = nullptr;
    struct { uint32_t struct_size; uint32_t vocab_size; uint32_t num_layers; uint32_t state_width; } edge_cap;
    struct { uint32_t struct_size; uint32_t vocab_size; uint32_t num_layers; uint32_t state_width; } segment_cap;
    uint32_t context_tokens = 2048;
    int n_threads = 4;
    int current_pos = 0;
    std::atomic<bool> is_aborted{false};
    std::mutex session_mutex;
    double ttft_ms = 0.0;
    int tokens_generated = 0;
    double tokens_per_sec = 0.0;
};

// Helpers that exist in colibri_jni_bridge.cpp — we pull them in via include
// to avoid duplicating the full bridge logic. We only need the parts we use.
#include "colibri_jni_bridge.cpp"

// ============================================================================
// JNI Exported Functions
// ============================================================================

// ---------------------------------------------------------------------------
// colibriInit  — (Ljava/lang/String;Ljava/lang/String;II)J
//   Creates a new Colibri engine instance initialized with the given model directory.
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriInit(
    JNIEnv* env, jobject thiz,
    jstring jmodel_dir, jstring jfamily, jint max_ctx, jint threads) {
    // Extract C strings from JNI
    const char* model_dir_c = jmodel_dir ? env->GetStringUTFChars(jmodel_dir, nullptr) : nullptr;
    const char* family_c = jfamily ? env->GetStringUTFChars(jfamily, nullptr) : nullptr;

    std::string model_dir = model_dir_c ? model_dir_c : "";
    std::string family = (family_c && strlen(family_c) > 0) ? family_c : "auto";

    // Release JNI strings immediately
    if (model_dir_c) env->ReleaseStringUTFChars(jmodel_dir, model_dir_c);
    if (family_c) env->ReleaseStringUTFChars(jfamily, family_c);

    // Validate model_dir
    if (model_dir.empty()) {
        return 0; // Kotlin will treat 0 as "failed"
    }

    // Auto-detect model family from directory contents if family == "auto"
    if (family == "auto" || family.empty()) {
        // Check for known model config files — order matters for priority
        if (access((model_dir + "/qwen38_core.json").c_str(), F_OK) == 0) {
            family = "qwen38";
        } else if (access((model_dir + "/qwen36_core.json").c_str(), F_OK) == 0) {
            family = "qwen36";
        } else if (access((model_dir + "/glm53_core.json").c_str(), F_OK) == 0) {
            family = "glm53";
        } else if (access((model_dir + "/olmoe_core.json").c_str(), F_OK) == 0) {
            family = "olmoe";
        } else if (access((model_dir + "/config.json").c_str(), F_OK) == 0) {
            family = "qwen38"; // default fallback
        } else {
            family = "qwen38"; // safest default
        }
    }

    // Allocate ColibriContext on the heap; will be stored as jlong pointer
    auto ctx = std::make_unique<ColibriContext>();
    ctx->model_dir = model_dir;
    ctx->family = family;
    ctx->context_tokens = static_cast<uint32_t>(max_ctx > 0 ? max_ctx : 2048);

    // Determine thread count: respect user request but cap to safe mobile bounds
    int num_cpus = sysconf(_SC_NPROCESSORS_CONF);
    int worker_threads = std::max(1, (threads > 0 ? threads : num_cpus - 2));
    // Enforce max 4 performance cores for mobile inference
    if (worker_threads > 4) {
        worker_threads = 4;
    }
    ctx->n_threads = worker_threads;

    // Cap context tokens at safe mobile bound
    if (ctx->context_tokens > 4096) {
        LOGW("Requested context tokens %u exceeds safe bound for mobile — capping at 4096.",
             ctx->context_tokens);
        ctx->context_tokens = 4096;
    }

    // Set environment variables that control Colibri runtime behaviour
    setenv("COLI_MMAP", "1", 1);
    setenv("COLI_MAP_EXPERTS", "1", 1);
    setenv("K3_MMAP", "1", 1);
    setenv("TRUNK_RESIDENT_LAYERS", "0", 1);
    setenv("COLI_DIRECT", "0", 1);
    setenv("MLOCK", "1", 1);
    setenv("COLI_MLOCK", "1", 1);
    char omp_str[32];
    snprintf(omp_str, sizeof(omp_str), "%d", ctx->n_threads);
    setenv("OMP_NUM_THREADS", omp_str, 1);

    char error[512] = {0};

    // ── 1. Open Edge Engine ─────────────────────────────────────────────────────
    ColiEdgeEngineOptions edge_opts = {
        sizeof(edge_opts),
        ctx->model_dir.c_str(),
        0,            // memory_limit_bytes
        0,            // backend_mask
        nullptr, 0,   // resource_plan / size
        {0, 0, 0}     // reserved
    };

    if (coli_edge_engine_open(ctx->family.c_str(), &edge_opts, &ctx->edge, error, sizeof(error)) != 0) {
        LOGE("Failed to open Colibri Edge Engine (%s): %s", ctx->family.c_str(), error);
        return 0;
    }

    ctx->edge_cap.struct_size = sizeof(ctx->edge_cap);
    if (coli_edge_engine_capabilities(ctx->edge, &ctx->edge_cap, error, sizeof(error)) != 0) {
        LOGE("Failed to get Colibri Edge capabilities: %s", error);
        return 0;
    }

    if (ctx->edge_cap.vocab_size == 0 || ctx->edge_cap.state_width == 0) {
        LOGE("Corrupted Colibri Edge model metadata: vocab_size=%u, state_width=%u",
             ctx->edge_cap.vocab_size, ctx->edge_cap.state_width);
        return 0;
    }

    // ── 2. Open Segment Engine (layers 0 .. num_layers) ────────────────────────
    ColiSegmentEngineOptions seg_opts = {
        sizeof(seg_opts),
        ctx->model_dir.c_str(),
        0,                                     // layer_begin (default from adapter)
        ctx->edge_cap.num_layers,              // layer_end (exclusive)
        ctx->context_tokens,                   // context_tokens
        0, 0, 0, nullptr, 0                    // reserved / resource_plan
    };

    if (coli_segment_engine_open(ctx->family.c_str(), &seg_opts, &ctx->segment, error, sizeof(error)) != 0) {
        LOGE("Failed to open Colibri Segment Engine (%s): %s", ctx->family.c_str(), error);
        return 0;
    }

    ctx->segment_cap.struct_size = sizeof(ctx->segment_cap);
    if (coli_segment_engine_capabilities(ctx->segment, &ctx->segment_cap, error, sizeof(error)) != 0) {
        LOGE("Failed to get Colibri Segment capabilities: %s", error);
        return 0;
    }

    // ── 3. Create Segment Session (KV cache + per-request state) ───────────────
    ColiSegmentSessionOptions sess_opts = {
        sizeof(sess_opts),
        ctx->context_tokens,
        0, {0, 0, 0, 0}  // memory_limit_bytes, reserved
    };

    if (coli_segment_session_create(ctx->segment, &sess_opts, &ctx->session, error, sizeof(error)) != 0) {
        LOGE("Failed to create Colibri Segment Session: %s", error);
        return 0;
    }

    // Return the opaque handle (jlong) to Kotlin
    jlong handle = reinterpret_cast<jlong>(ctx.release());
    LOGI("Colibri Engine initialized: family='%s', vocab=%u, layers=%u, width=%u, ctx=%u, threads=%u",
         ctx->family.c_str(), ctx->edge_cap.vocab_size, ctx->edge_cap.num_layers,
         ctx->edge_cap.state_width, ctx->context_tokens, ctx->n_threads);

    return handle;
}

// ---------------------------------------------------------------------------
// colibriLoadModel  — (JLjava/lang/String;)I
//   Re-validates / overrides family for a previously-initialized handle.
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriLoadModel(
    JNIEnv* env, jobject thiz, jlong handle, jstring jfamily) {
    if (handle == 0) {
        return COLIBRI_STATUS_NOT_INITIALIZED;
    }

    auto* ctx = reinterpret_cast<ColibriContext*>(handle);
    if (!ctx) {
        return COLIBRI_STATUS_NOT_INITIALIZED;
    }

    // Extract optional family override
    const char* family_c = env->GetStringUTFChars(jfamily, nullptr);
    std::string family = (family_c && strlen(family_c) > 0) ? family_c : ctx->family;

    if (family_c) env->ReleaseStringUTFChars(jfamily, family_c);

    // If family changed, we can't re-initialize safely from this bridge level
    // without the full Kotlin-side teardown. For now just validate the
    // existing engine is still alive by checking capabilities.
    if (family != ctx->family) {
        LOGW("colibriLoadModel family override requested but not re-initializing; "
             "using family='%s' from init", ctx->family.c_str());
    }

    // Validate that the edge and segment engines are still usable
    char error[512] = {0};
    if (coli_edge_engine_capabilities(ctx->edge, &ctx->edge_cap, error, sizeof(error)) != 0) {
        LOGE("Engine capabilities check failed: %s", error);
        return COLIBRI_STATUS_RUNTIME_ERROR;
    }
    if (ctx->edge_cap.vocab_size == 0 || ctx->edge_cap.state_width == 0) {
        return COLIBRI_STATUS_RUNTIME_ERROR;
    }

    return COLIBRI_STATUS_OK;
}

// ---------------------------------------------------------------------------
// colibriGenerateSync  — (JLjava/lang/String;FF)Ljava/lang/String;
//   Synchronous generation: blocks until max_tokens are generated (or EOS),
//   then returns the full completed text as a Java String.
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriGenerateSync(
    JNIEnv* env, jobject thiz, jlong handle,
    jstring jprompt, jint max_tokens, jfloat temperature, jfloat top_p) {
    if (handle == 0) {
        return env->NewStringUTF("");
    }

    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    // Extract prompt string from JNI
    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c ? prompt_c : "");
    if (prompt_c) env->ReleaseStringUTFChars(jprompt, prompt_c);

    if (prompt.empty()) {
        return env->NewStringUTF("");
    }

    char error[512] = {0};

    // ── Tokenize the prompt ───────────────────────────────────────────────────
    size_t prompt_count = 0;
    if (coli_edge_tokenize(ctx->edge, prompt.c_str(), prompt.length(),
                          nullptr, 0, &prompt_count, error, sizeof(error)) != 0 ||
        prompt_count == 0) {
        LOGE("Prompt tokenize sizing failed: %s", error);
        return env->NewStringUTF("");
    }

    std::vector<int32_t> prompt_tokens(prompt_count);
    if (coli_edge_tokenize(ctx->edge, prompt.c_str(), prompt.length(),
                          prompt_tokens.data(), prompt_count, &prompt_count, error, sizeof(error)) != 0) {
        LOGE("Prompt tokenize encode failed: %s", error);
        return env->NewStringUTF("");
    }

    // If prompt exceeds context, truncate from the front to fit
    if (prompt_count >= ctx->context_tokens) {
        LOGW("Prompt exceeds context token capacity (%u/%u); truncating.",
             prompt_count, ctx->context_tokens);
        size_t trim = prompt_count - ctx->context_tokens + 1;
        if (trim > prompt_tokens.size()) trim = prompt_tokens.size();
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.begin() + trim);
        prompt_count = prompt_tokens.size();
    }

    // ── Prefill: run embed + segment run for the prompt ───────────────────────
    size_t state_width = ctx->edge_cap.state_width;
    size_t row_bytes = state_width * sizeof(float);

    std::vector<float> input(prompt_count * state_width);
    std::vector<float> output(prompt_count * state_width);

    ColiEdgeEmbedRequest embed = {
        sizeof(embed),
        static_cast<uint32_t>(prompt_count),
        prompt_tokens.data(),
        prompt_count,
        input.data(),
        input.size() * sizeof(float),
        coli_cancel_check,
        ctx,
        {0, 0, 0}
    };

    if (coli_edge_embed(ctx->edge, &embed, error, sizeof(error)) != 0) {
        LOGE("coli_edge_embed prefill failed: %s", error);
        return env->NewStringUTF("");
    }

    ColiSegmentRunRequest run = {
        sizeof(run),
        static_cast<uint32_t>(prompt_count),
        0, // start position = 0
        prompt_tokens.data(),
        prompt_count,
        input.data(),
        input.size() * sizeof(float),
        output.data(),
        output.size() * sizeof(float),
        coli_cancel_check,
        ctx,
        {0, 0, 0, 0}
    };

    if (coli_segment_run(ctx->session, &run, error, sizeof(error)) != 0) {
        LOGE("coli_segment_run prefill failed: %s", error);
        return env->NewStringUTF("");
    }

    // ── Autoregressive decode loop ────────────────────────────────────────────
    float* last_hidden = output.data() + (prompt_count - 1) * state_width;
    int32_t current_token = -1;

    // Sample first token from prompt position
    if (temperature <= 0.01f) {
        float score = 0.0f;
        ColiEdgeSelectRequest select = {
            sizeof(select),
            1,
            last_hidden,
            row_bytes,
            &current_token,
            1,
            &score,
            1,
            coli_cancel_check,
            ctx,
            {0, 0, 0}
        };
        if (coli_edge_select(ctx->edge, &select, error, sizeof(error)) != 0) {
            LOGE("coli_edge_select first token failed: %s", error);
            return env->NewStringUTF("");
        }
    } else {
        // Argmax fallback for non-greedy sampling
        std::vector<float> logits(ctx->edge_cap.vocab_size);
        ColiEdgeLogitsRequest logits_req = {
            sizeof(logits_req),
            1,
            last_hidden,
            row_bytes,
            logits.data(),
            ctx->edge_cap.vocab_size,
            coli_cancel_check,
            ctx,
            {0, 0, 0}
        };
        if (coli_edge_logits(ctx->edge, &logits_req, error, sizeof(error)) != 0) {
            LOGE("coli_edge_logits first token failed: %s", error);
            return env->NewStringUTF("");
        }
        float max_logit = -1e30f;
        for (uint32_t i = 0; i < ctx->edge_cap.vocab_size; i++) {
            if (std::isfinite(logits[i]) && logits[i] > max_logit) {
                max_logit = logits[i];
            }
        }
        current_token = (max_logit > -1e30f) ? static_cast<int32_t>(
            std::distance(logits.data(), std::max_element(logits.begin(), logits.end()))) : 0;
    }

    // Build result text from detokenized pieces
    std::string result_text;
    std::string carry;
    int n_generated = 0;
    uint32_t current_pos = static_cast<uint32_t>(prompt_count);
    int max_gen = max_tokens > 0 ? static_cast<int>(max_tokens) : 512;

    // Per-step buffers (1-row)
    std::vector<float> step_in(state_width);
    std::vector<float> step_out(state_width);

    while (n_generated < max_gen && current_pos < ctx->context_tokens) {
        // Check for EOS
        if (current_token == ctx->edge_cap.eos_token_id) {
            LOGI("EOS token reached after %d generated tokens", n_generated);
            break;
        }

        // Detokenize current token into text piece
        size_t detok_bytes = 0;
        char detok_error[512] = {0};
        if (coli_edge_detokenize(ctx->edge, &current_token, 1,
                                  nullptr, 0, &detok_bytes, detok_error, sizeof(detok_error)) != 0 ||
            detok_bytes == 0) {
            // Fallback: append token ID
            char buf[32];
            snprintf(buf, sizeof(buf), "%d ", current_token);
            result_text.append(buf);
        } else {
            std::vector<char> detok_buf(detok_bytes + 1, 0);
            if (coli_edge_detokenize(ctx->edge, &current_token, 1,
                                      detok_buf.data(), detok_buf.size(), &detok_bytes,
                                      detok_error, sizeof(detok_error)) != 0) {
                LOGW("Detokenize failed: %s", detok_error);
                char buf[32];
                snprintf(buf, sizeof(buf), "%d ", current_token);
                result_text.append(buf);
            } else {
                result_text.append(detok_buf.data(), detok_bytes);
            }
        }

        n_generated++;

        if (n_generated >= max_gen || current_pos >= ctx->context_tokens - 1) {
            break;
        }

        // Embed the current token as the next step's input
        ColiEdgeEmbedRequest step_embed = {
            sizeof(step_embed),
            1,
            &current_token,
            1,
            step_in.data(),
            row_bytes,
            coli_cancel_check,
            ctx,
            {0, 0, 0}
        };
        if (coli_edge_embed(ctx->edge, &step_embed, error, sizeof(error)) != 0) {
            LOGE("coli_edge_embed step failed: %s", error);
            break;
        }

        ColiSegmentRunRequest step_run = {
            sizeof(step_run),
            1,
            static_cast<uint64_t>(current_pos),
            &current_token,
            1,
            step_in.data(),
            row_bytes,
            step_out.data(),
            row_bytes,
            coli_cancel_check,
            ctx,
            {0, 0, 0, 0}
        };
        if (coli_segment_run(ctx->session, &step_run, error, sizeof(error)) != 0) {
            LOGE("coli_segment_run step failed: %s", error);
            break;
        }

        current_pos++;

        // Sample next token
        if (temperature <= 0.01f) {
            float score = 0.0f;
            ColiEdgeSelectRequest select = {
                sizeof(select),
                1,
                step_out.data(),
                row_bytes,
                &current_token,
                1,
                &score,
                1,
                coli_cancel_check,
                ctx,
                {0, 0, 0}
            };
            if (coli_edge_select(ctx->edge, &select, error, sizeof(error)) != 0) {
                LOGE("coli_edge_select next token failed: %s", error);
                break;
            }
        } else {
            std::vector<float> logits(ctx->edge_cap.vocab_size);
            ColiEdgeLogitsRequest logits_req = {
                sizeof(logits_req),
                1,
                step_out.data(),
                row_bytes,
                logits.data(),
                ctx->edge_cap.vocab_size,
                coli_cancel_check,
                ctx,
                {0, 0, 0}
            };
            if (coli_edge_logits(ctx->edge, &logits_req, error, sizeof(error)) != 0) {
                LOGE("coli_edge_logits next token failed: %s", error);
                // Argmax fallback
                float max_l = -1e30f;
                for (uint32_t i = 0; i < ctx->edge_cap.vocab_size; i++) {
                    if (std::isfinite(logits[i]) && logits[i] > max_l) {
                        max_l = logits[i];
                    }
                }
                current_token = (max_l > -1e30f) ? static_cast<int32_t>(
                    std::distance(logits.data(), std::max_element(logits.begin(), logits.end()))) : 0;
            } else {
                current_token = static_cast<int32_t>(
                    std::distance(logits.data(), std::max_element(logits.begin(), logits.end())));
            }
        }
    }

    // Return the accumulated generated text
    return env->NewStringUTF(result_text.c_str());
}

// ---------------------------------------------------------------------------
// colibriFree  — (J)V
//   Releases all native resources associated with the given handle.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL Java_com_deepeye_agent_domain_engine_ColibriEngine_colibriFree(
    JNIEnv* env, jobject thiz, jlong handle) {
    if (handle == 0) {
        return;
    }

    auto* ctx = reinterpret_cast<ColibriContext*>(handle);
    LOGI("Releasing Colibri engine resources (handle: %p)", reinterpret_cast<void*>(handle));

    // Destruction order: session -> segment -> edge (reverse of init)
    if (ctx->session) {
        coli_segment_session_destroy(ctx->session);
        ctx->session = nullptr;
    }
    if (ctx->segment) {
        coli_segment_engine_close(ctx->segment, nullptr, 0);
        ctx->segment = nullptr;
    }
    if (ctx->edge) {
        coli_edge_engine_close(ctx->edge);
        ctx->edge = nullptr;
    }

    // Free the ColibriContext itself
    delete ctx;
}

// ============================================================================
// End of colibri_bridge.cpp
// ============================================================================