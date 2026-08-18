// =============================================================================
// llama-bridge.cpp — DeepEyeLLM Native JNI Bridge
// =============================================================================
// Real llama.cpp inference with token streaming via JNI callbacks.
//
// Architecture:
//   Kotlin (LlamaCppEngine) ──JNI──▶ C++ (this file) ──▶ llama.cpp
//
// Key functions:
//   nativeInitModel()                — Load GGUF, create context + sampler
//   nativeGenerateResponse()         — Synchronous inference (returns full string)
//   nativeGenerateResponseStream()   — Streaming inference (per-token JNI callback)
//   nativeAbortGeneration()          — Set abort flag to stop mid-generation
//   nativeFreeModel()                — Free all native resources
// =============================================================================

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <chrono>
#include <sys/resource.h>
#include <android/log.h>

// llama.cpp core API
#include "llama.h"
#include "ggml-backend.h"

// ── Logging macros ──────────────────────────────────────────────────────────
#define LOG_TAG "DeepEyeLLM-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Fallback if not defined in the llama.cpp version being used
#ifndef LLAMA_DEFAULT_SEED
#define LLAMA_DEFAULT_SEED 0xFFFFFFFF
#endif

// =============================================================================
// SECTION 1: Native State
// =============================================================================

// Holds all llama.cpp objects for one loaded model instance.
// Pointer to this struct is passed as `jlong handle` across JNI.
struct LlamaState {
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* sampler = nullptr;

    std::string model_path;
    int n_ctx     = 2048;
    int n_threads = 4;

    // Performance tracking
    double ttft_ms = 0.0;
    int tokens_generated = 0;
    double tokens_per_sec = 0.0;

    // Set to true by nativeAbortGeneration() to break out of the decode loop.
    // Checked every iteration — safe for cross-thread access.
    std::atomic<bool> abort_flag{false};
};

// Global backend state variables
static int g_backend_type = 0;
static int g_n_gpu_layers = 0;

// Performance counters
static std::chrono::steady_clock::time_point g_first_token_time;
static int g_tokens_generated = 0;
static double g_last_ttft_ms = 0.0;
static double g_last_tps = 0.0;

// Global JavaVM pointer — cached in JNI_OnLoad for thread-attach operations.
static JavaVM* g_vm = nullptr;

static void deepEyeLlamaLogCallback(enum ggml_log_level level, const char* text, void* /*user_data*/) {
    if (!text) return;
    int android_level = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_WARN) android_level = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_ERROR) android_level = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_INFO) android_level = ANDROID_LOG_INFO;
    __android_log_print(android_level, LOG_TAG, "%s", text);
}

// =============================================================================
// SECTION 2: JNI Lifecycle
// =============================================================================

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;

    llama_log_set(deepEyeLlamaLogCallback, nullptr);
    llama_backend_init();
    LOGI("JNI_OnLoad: JavaVM cached, llama backend and logger initialized.");

    return JNI_VERSION_1_6;
}

// =============================================================================
// SECTION 3: JNI Callback Helpers
// =============================================================================
// These safely invoke methods on the Kotlin NativeTokenCallback object.
// They handle null checks and prevent JNI local reference table overflow
// by deleting jstring refs inside the loop.

static void safeCallToken(JNIEnv* env, jobject callback, jmethodID method, const std::string& token) {
    if (!callback || !method) return;
    bool needsDetach = false;
    JNIEnv* currentEnv = env;
    if (g_vm && g_vm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&currentEnv, nullptr) == JNI_OK) {
            needsDetach = true;
        } else {
            return;
        }
    }
    jstring jtoken = currentEnv->NewStringUTF(token.c_str());
    currentEnv->CallVoidMethod(callback, method, jtoken);
    currentEnv->DeleteLocalRef(jtoken);
    if (needsDetach && g_vm) g_vm->DetachCurrentThread();
}

static void safeCallComplete(JNIEnv* env, jobject callback, jmethodID method) {
    if (!callback || !method) return;
    bool needsDetach = false;
    JNIEnv* currentEnv = env;
    if (g_vm && g_vm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&currentEnv, nullptr) == JNI_OK) {
            needsDetach = true;
        } else {
            return;
        }
    }
    currentEnv->CallVoidMethod(callback, method);
    if (needsDetach && g_vm) g_vm->DetachCurrentThread();
}

static void safeCallError(JNIEnv* env, jobject callback, jmethodID method, const std::string& errorMsg) {
    if (!callback || !method) return;
    bool needsDetach = false;
    JNIEnv* currentEnv = env;
    if (g_vm && g_vm->GetEnv(reinterpret_cast<void**>(&currentEnv), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&currentEnv, nullptr) == JNI_OK) {
            needsDetach = true;
        } else {
            return;
        }
    }
    jstring jmsg = currentEnv->NewStringUTF(errorMsg.c_str());
    currentEnv->CallVoidMethod(callback, method, jmsg);
    currentEnv->DeleteLocalRef(jmsg);
    if (needsDetach && g_vm) g_vm->DetachCurrentThread();
}

// =============================================================================
// SECTION 4: Tokenization & Utility Helpers
// =============================================================================

// Tokenize a string using the model's vocabulary.
// Handles the two-pass pattern: first call gets required size, second fills.
static std::vector<llama_token> tokenize_prompt(
    const llama_model* model,
    const std::string& text,
    bool add_special,    // Add BOS token if the model uses one
    bool parse_special   // Parse special tokens like <|im_start|> in the text
) {
    const struct llama_vocab* vocab = llama_model_get_vocab(model);
    // First pass: estimate buffer size (text length + margin)
    int n_tokens = text.length() + 256;
    std::vector<llama_token> tokens(n_tokens);

    n_tokens = llama_tokenize(
        vocab,
        text.c_str(),
        (int32_t)text.length(),
        tokens.data(),
        (int32_t)tokens.size(),
        add_special,
        parse_special
    );

    // If negative, the buffer was too small — absolute value is required size
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            vocab,
            text.c_str(),
            (int32_t)text.length(),
            tokens.data(),
            (int32_t)tokens.size(),
            add_special,
            parse_special
        );
    }

    tokens.resize(n_tokens);
    return tokens;
}

// Convert a single token ID to its text representation.
// Handles the two-pass pattern for tokens that exceed the initial buffer.
static std::string token_to_piece(const llama_model* model, llama_token token) {
    const struct llama_vocab* vocab = llama_model_get_vocab(model);
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);

    if (n < 0) {
        // Token text is longer than 256 bytes (rare, but possible for merged tokens)
        std::vector<char> large_buf(-n);
        n = llama_token_to_piece(vocab, token, large_buf.data(), (int32_t)large_buf.size(), 0, false);
        return std::string(large_buf.data(), n);
    }

    return std::string(buf, n);
}

// Reassemble complete UTF-8 characters from a token-by-token byte stream.
//
// llama.cpp may split a single multi-byte UTF-8 character (e.g. an emoji whose
// leading bytes are F0 9F 98 80) across two adjacent *token pieces*. JNI's
// NewStringUTF rejects truncated multi-byte sequences and aborts the process,
// so we must only forward *complete, valid* characters to Kotlin.
//
// `carry` accumulates incoming bytes between calls; `complete` receives only
// valid UTF-8 output. Any incomplete trailing multi-byte sequence is retained
// in `carry` until the next piece supplies the continuation bytes. Invalid or
// interrupted sequences are replaced with U+FFFD so the stream never forwards
// malformed bytes to JNI and never stalls on corrupt input.
static void drainCompleteUtf8(std::string& carry, const std::string& piece, std::string& complete) {
    if (!piece.empty()) {
        carry += piece;
    }
    if (carry.empty()) return;

    static const std::string kReplacement = "\xEF\xBF\xBD";  // U+FFFD

    size_t i = 0;
    const size_t n = carry.size();
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(carry[i]);

        size_t len;
        if (c < 0x80) {
            len = 1;                                    // ASCII
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;                                    // 2-byte sequence
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;                                    // 3-byte sequence
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;                                    // 4-byte sequence
        } else {
            // Invalid lead byte (stray continuation 0x80..0xBF, 0xF5..0xFF).
            complete += kReplacement;
            i += 1;
            continue;
        }

        if (len == 1) {
            complete.push_back(static_cast<char>(c));
            i += 1;
            continue;
        }

        // Count how many valid continuation bytes are present after the lead.
        const size_t avail = n - i - 1;
        size_t have = 0;
        while (have < avail && have < len - 1) {
            if ((static_cast<unsigned char>(carry[i + 1 + have]) & 0xC0) != 0x80) break;
            have++;
        }

        if (have < len - 1) {
            if (have >= avail) {
                // Not enough bytes yet — this may be completed by a later piece.
                break;   // retain the rest of `carry`
            }
            // Enough bytes exist but one is not a continuation byte.
            // Substitute the malformed lead and re-evaluate from the next byte.
            complete += kReplacement;
            i += 1;
            continue;
        }

        complete.append(carry, i, len);   // forward the whole valid character
        i += len;
    }

    if (i > 0) {
        carry.erase(0, i);   // remove the bytes we have consumed or replaced
    }
}

// Apply the model's built-in chat template (e.g., ChatML for Qwen3).
// Returns the formatted prompt string, or the raw prompt if no template is available.
static std::string apply_chat_template(
    const llama_model* model,
    const std::string& user_prompt
) {
    // Build a minimal chat message array: system + user
    std::vector<llama_chat_message> messages = {
        { "system", "You are a helpful assistant." },
        { "user",   user_prompt.c_str()            }
    };

    const char* tmpl = llama_model_chat_template(model, nullptr);

    // First call with null buffer to get required size
    int32_t template_len = llama_chat_apply_template(
        tmpl,
        messages.data(),
        messages.size(),
        true,                       // add_ass = true (append "assistant\n" start)
        nullptr,                    // null buffer = just return required size
        0
    );

    if (template_len > 0) {
        // Second call to actually fill the buffer
        std::vector<char> buf(template_len + 1);
        llama_chat_apply_template(
            tmpl,
            messages.data(),
            messages.size(),
            true,
            buf.data(),
            (int32_t)buf.size()
        );
        LOGI("Chat template applied (formatted length: %d chars)", template_len);
        return std::string(buf.data(), template_len);
    }

    // Fallback: model has no chat template metadata — use raw prompt
    LOGW("No chat template in GGUF metadata, using raw prompt");
    return user_prompt;
}

// =============================================================================
// SECTION 5: Core Inference Engine
// =============================================================================
// Shared logic used by both synchronous and streaming JNI functions.
//
// token_callback: called for each generated token text.
//                 Return false to stop generation early.
//
// Returns: number of tokens generated (>= 0), or -1 on error.
//          On error, error_out is populated with a description.

static int run_inference(
    LlamaState* state,
    const std::string& prompt,
    int max_tokens,
    const std::function<bool(const std::string& piece)>& token_callback,
    std::string& error_out
) {
    // ── Step 1: Apply chat template ─────────────────────────────────────────
    std::string formatted = apply_chat_template(state->model, prompt);

    // ── Step 2: Tokenize ────────────────────────────────────────────────────
    // add_special = false when template is applied (template handles BOS/markers)
    // parse_special = true to recognize <|im_start|> etc. in the formatted text
    bool has_template = (formatted != prompt);
    auto prompt_tokens = tokenize_prompt(
        state->model,
        formatted,
        !has_template,   // add_special only if NO template was applied
        true             // always parse_special
    );

    int n_prompt = (int)prompt_tokens.size();
    LOGI("Prompt tokenized: %d tokens", n_prompt);

    // ── Step 3: Validate against context size ───────────────────────────────
    int n_ctx = llama_n_ctx(state->ctx);
    if (n_prompt >= n_ctx) {
        error_out = "Prompt too long: " + std::to_string(n_prompt) +
                    " tokens exceeds context of " + std::to_string(n_ctx);
        LOGE("%s", error_out.c_str());
        return -1;
    }

    // Cap max_tokens to remaining context space
    int max_gen = std::min(max_tokens, n_ctx - n_prompt);

    // ── Step 4: Clear KV cache for fresh generation ─────────────────────────
    // Each call is independent — no conversation history retained in KV cache.
    // (The Kotlin side can send full conversation history each time if needed.)
    llama_memory_clear(llama_get_memory(state->ctx), true);

    // Reset sampler state (clears any leftover state from previous generation)
    llama_sampler_reset(state->sampler);

    // ── Step 5: Process prompt tokens (prefill) ─────────────────────────────
    // Feed in chunks of 512 to avoid allocating one giant batch.
    auto t_prefill_start = std::chrono::high_resolution_clock::now();
    const int BATCH_CHUNK = 512;
    for (int i = 0; i < n_prompt; i += BATCH_CHUNK) {
        if (state->abort_flag.load(std::memory_order_relaxed)) {
            LOGI("Aborted during prompt processing");
            return 0;
        }

        int n_eval = std::min(BATCH_CHUNK, n_prompt - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, n_eval);

        if (llama_decode(state->ctx, batch) != 0) {
            error_out = "llama_decode failed during prompt processing (chunk at offset "
                        + std::to_string(i) + ")";
            LOGE("%s", error_out.c_str());
            return -1;
        }
    }
    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    double prefill_ms = std::chrono::duration<double, std::milli>(t_prefill_end - t_prefill_start).count();
    double prefill_tok_s = (prefill_ms > 0.0) ? (n_prompt / (prefill_ms / 1000.0)) : 0.0;

    LOGI("Prompt prefill complete: %d tokens in %.1f ms (%.1f tok/s). Starting generation (max=%d)...",
         n_prompt, prefill_ms, prefill_tok_s, max_gen);

    // ── Step 6: Autoregressive generation loop ──────────────────────────────
    auto t_start = std::chrono::steady_clock::now();
    int n_generated = 0;
    bool first_token = true;
    state->tokens_generated = 0;

    for (int i = 0; i < max_gen; i++) {
        // Check abort flag (set by nativeAbortGeneration from another thread)
        if (state->abort_flag.load(std::memory_order_relaxed)) {
            LOGI("Generation aborted by user after %d tokens", n_generated);
            break;
        }

        // ── Sample the next token from the model's logits ───────────────────
        // idx = -1 means "use logits from the last token in the context"
        llama_token new_token = llama_sampler_sample(state->sampler, state->ctx, -1);

        if (first_token) {
            auto t_first = std::chrono::steady_clock::now();
            state->ttft_ms = std::chrono::duration<double, std::milli>(t_first - t_start).count();
            first_token = false;
        }

        // ── Check for End-Of-Generation ─────────────────────────────────────
        if (llama_vocab_is_eog(llama_model_get_vocab(state->model), new_token)) {
            LOGI("EOG token reached after %d generated tokens", n_generated);
            break;
        }

        // ── Convert token to text piece ─────────────────────────────────────
        std::string piece = token_to_piece(state->model, new_token);

        // ── Deliver token to caller ─────────────────────────────────────────
        if (!token_callback(piece)) {
            LOGI("Token callback requested stop after %d tokens", n_generated);
            break;
        }

        // ── Feed the new token back into the model for next iteration ───────
        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(state->ctx, batch) != 0) {
            error_out = "llama_decode failed at generated token " + std::to_string(i);
            LOGE("%s", error_out.c_str());
            return -1;
        }

        n_generated++;
        state->tokens_generated++;
    }

    // ── Performance logging ─────────────────────────────────────────────────
    auto t_end = std::chrono::steady_clock::now();
    double elapsed_s = std::chrono::duration<double>(t_end - t_start).count();
    state->tokens_per_sec = (elapsed_s > 0.0) ? n_generated / elapsed_s : 0.0;
    int ttft = static_cast<int>(state->ttft_ms);

    LOGI("Performance: TTFT=%d ms, tokens/sec=%.2f", ttft, state->tokens_per_sec);
    LOGI("Generation complete: %d tokens in %.2fs (%.1f tok/s)", n_generated, elapsed_s, state->tokens_per_sec);

    return n_generated;
}

// =============================================================================
// SECTION 6: JNI Exported Functions
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// nativeInitModel — Load a GGUF model file, create context and sampler chain.
//
// Returns: opaque handle (pointer to LlamaState), or 0 on failure.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jlong JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeInitModel(
    JNIEnv* env, jobject thiz, jstring jmodel_path, jint n_ctx, jint n_threads, jint n_gpu_layers) {

    const char* path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGI("Using backend type %d with %d GPU layers", g_backend_type, g_n_gpu_layers);
    LOGI("═══════════════════════════════════════════════════════");
    LOGI("Loading GGUF model: %s", path);
    LOGI("  Context size: %d tokens", n_ctx);
    LOGI("  Threads: %d", n_threads);
    LOGI("  Requested GPU layers: %d (global: %d)", n_gpu_layers, g_n_gpu_layers);
    LOGI("═══════════════════════════════════════════════════════");

    // Boost process priority for unthrottled maximum CPU frequency
    setpriority(PRIO_PROCESS, 0, -20);

    auto t_start = std::chrono::high_resolution_clock::now();
    int effective_gpu_layers = (g_n_gpu_layers > 0) ? g_n_gpu_layers : n_gpu_layers;

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = effective_gpu_layers;
    model_params.main_gpu     = 0;
    model_params.split_mode   = static_cast<enum llama_split_mode>(0);
    model_params.load_mode    = LLAMA_LOAD_MODE_MMAP;

    llama_model* model = llama_model_load_from_file(path, model_params);
    if (!model && effective_gpu_layers > 0) {
        LOGW("GPU layer allocation failed for %s. Retrying with CPU mode (0 GPU layers)...", path);
        model_params.n_gpu_layers = 0;
        model = llama_model_load_from_file(path, model_params);
    }

    if (!model) {
        LOGE("FATAL: Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(jmodel_path, path);
        return 0;
    }

    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx            = n_ctx;
    ctx_params.n_threads        = std::max(1, n_threads);
    ctx_params.n_threads_batch  = std::max(1, n_threads);
    ctx_params.n_batch          = 1024;
    ctx_params.n_ubatch         = 512;
    ctx_params.flash_attn_type  = LLAMA_FLASH_ATTN_TYPE_AUTO;
    ctx_params.type_k           = GGML_TYPE_F16;
    ctx_params.type_v           = GGML_TYPE_F16;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGW("Failed to create llama context with default params; retrying with safe fallback...");
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
        ctx = llama_init_from_model(model, ctx_params);
    }

    if (!ctx) {
        LOGE("FATAL: Failed to create llama context");
        llama_model_free(model);
        env->ReleaseStringUTFChars(jmodel_path, path);
        return 0;
    }

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    auto* state = new LlamaState();
    state->model      = model;
    state->ctx        = ctx;
    state->sampler    = sampler;
    state->model_path = std::string(path);
    state->n_ctx      = n_ctx;
    state->n_threads  = n_threads;

    env->ReleaseStringUTFChars(jmodel_path, path);

    auto t_end = std::chrono::high_resolution_clock::now();
    double load_time = std::chrono::duration<double>(t_end - t_start).count();
    LOGI("Model loaded successfully in %.1fs (handle created)", load_time);

    return reinterpret_cast<jlong>(state);
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGenerateResponse — Synchronous inference. Returns the full response.
//
// Blocks until generation is complete or aborted.
// For UI typing effects, prefer nativeGenerateResponseStream instead.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jstring JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeGenerateResponse(
    JNIEnv* env, jobject thiz, jlong handle, jstring jprompt) {

    if (handle == 0) {
        return env->NewStringUTF("Error: Invalid native context handle");
    }

    auto* state = reinterpret_cast<LlamaState*>(handle);
    state->abort_flag.store(false);

    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c ? prompt_c : "");
    env->ReleaseStringUTFChars(jprompt, prompt_c);

    LOGI("Synchronous generation for prompt length: %zu chars", prompt.length());

    // Accumulate all tokens into a single string
    std::string output;
    std::string error_msg;

    int result = run_inference(state, prompt, 512,
        [&output](const std::string& piece) -> bool {
            output += piece;
            return true;  // Keep generating
        },
        error_msg
    );

    if (result < 0) {
        return env->NewStringUTF(("Error: " + error_msg).c_str());
    }

    return env->NewStringUTF(output.c_str());
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGenerateResponseStream — Streaming inference with per-token JNI callback.
//
// For each generated token, calls callback.onTokenGenerated(String token).
// On completion, calls callback.onGenerationComplete().
// On error, calls callback.onGenerationError(String message).
//
// This function BLOCKS the calling thread (Kotlin must call from Dispatchers.IO).
// Use nativeAbortGeneration() from another thread to cancel mid-generation.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeGenerateResponseStream(
    JNIEnv* env, jobject thiz, jlong handle, jstring jprompt, jint max_tokens,
    jobject callback) {

    if (handle == 0 || !callback) return;

    auto* state = reinterpret_cast<LlamaState*>(handle);
    state->abort_flag.store(false);

    // ── Resolve JNI callback methods ────────────────────────────────────────
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod    = env->GetMethodID(callbackClass, "onTokenGenerated",    "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onGenerationComplete", "()V");
    jmethodID onErrorMethod    = env->GetMethodID(callbackClass, "onGenerationError",    "(Ljava/lang/String;)V");
    env->DeleteLocalRef(callbackClass);

    if (!onTokenMethod) {
        LOGE("FATAL: Failed to find onTokenGenerated method on callback object");
        return;
    }

    // ── Extract prompt string ───────────────────────────────────────────────
    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c ? prompt_c : "");
    env->ReleaseStringUTFChars(jprompt, prompt_c);

    LOGI("Streaming generation started (prompt: %zu chars, max_tokens: %d)",
         prompt.length(), max_tokens);

    // ── Run inference with streaming callback ───────────────────────────────
    std::string error_msg;
    // Holds an incomplete trailing multi-byte UTF-8 sequence (e.g. the leading
    // bytes of an emoji) until the continuation bytes arrive in a later piece.
    std::string utf8_carry;

    int result = run_inference(state, prompt, max_tokens,
        [&](const std::string& piece) -> bool {
            // Reassemble UTF-8 across token boundaries. llama.cpp may split a
            // single multi-byte character (e.g. an emoji) across two pieces,
            // and JNI's NewStringUTF aborts on truncated sequences, so only
            // forward complete characters to Kotlin.
            std::string complete;
            drainCompleteUtf8(utf8_carry, piece, complete);

            if (!complete.empty()) {
                safeCallToken(env, callback, onTokenMethod, complete);
            }

            // Check if Kotlin-side threw an exception (e.g., consumer cancelled)
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                LOGW("Exception in onTokenGenerated callback, stopping generation");
                return false;  // Stop generating
            }

            // Check abort flag (set by nativeAbortGeneration)
            return !state->abort_flag.load(std::memory_order_relaxed);
        },
        error_msg
    );

    // Drop any incomplete trailing bytes left at end of generation. They are
    // not valid UTF-8 on their own, so passing them to NewStringUTF would crash.
    if (!utf8_carry.empty()) {
        LOGW("Discarding %zu trailing bytes of an incomplete UTF-8 character", utf8_carry.size());
        utf8_carry.clear();
    }

    // ── Deliver final status to Kotlin ──────────────────────────────────────
    if (result < 0) {
        safeCallError(env, callback, onErrorMethod, error_msg);
    } else {
        safeCallComplete(env, callback, onCompleteMethod);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeAbortGeneration — Set the abort flag to stop generation early.
//
// Thread-safe: can be called from any thread (Kotlin cancellation handler).
// The decode loop checks this flag every iteration and breaks if set.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeAbortGeneration(
    JNIEnv* env, jobject thiz, jlong handle) {

    if (handle == 0) return;
    auto* state = reinterpret_cast<LlamaState*>(handle);
    state->abort_flag.store(true, std::memory_order_release);
    LOGI("Abort flag set — generation will stop at next token boundary");
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeFreeModel — Release all native resources for a loaded model.
//
// Must be called when the engine is closed or the model is switched.
// After this call, the handle is invalid and must not be reused.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeFreeModel(
    JNIEnv* env, jobject thiz, jlong handle) {

    if (handle == 0) return;

    auto* state = reinterpret_cast<LlamaState*>(handle);
    if (!state) return;

    LOGI("Freeing native resources for model: %s", state->model_path.c_str());

    state->abort_flag.store(true);

    if (state->sampler) {
        llama_sampler_free(state->sampler);
        state->sampler = nullptr;
    }
    if (state->ctx) {
        llama_free(state->ctx);
        state->ctx = nullptr;
    }
    if (state->model) {
        llama_model_free(state->model);
        state->model = nullptr;
    }

    delete state;
    LOGI("Native resources freed successfully.");
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeSetBackendConfig — Configure GPU/NPU hardware backend offloading.
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeSetBackendConfig(
    JNIEnv* env, jobject thiz, jlong handle, jint backend_type, jint n_gpu_layers) {

    g_backend_type = backend_type;
    g_n_gpu_layers = n_gpu_layers;

    LOGI("Native backend config updated: type=%d, layers=%d", g_backend_type, g_n_gpu_layers);
}

extern "C" JNIEXPORT void JNICALL
Java_com_deepeye_agent_core_hardware_HardwareBackendSelector_nativeSetBackendConfig(
    JNIEnv* env, jobject thiz, jlong handle, jint backend_type, jint n_gpu_layers) {
    Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeSetBackendConfig(env, thiz, handle, backend_type, n_gpu_layers);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_deepeye_agent_domain_engine_LlamaCppEngine_nativeGetPerformanceStats(
    JNIEnv* env, jobject thiz, jlong handle) {

    if (handle == 0) return nullptr;
    auto* state = reinterpret_cast<LlamaState*>(handle);

    jclass statsClass = env->FindClass("com/deepeye/agent/domain/engine/PerformanceStats");
    if (!statsClass) {
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(statsClass, "<init>", "(ID)V");
    if (!ctor) {
        return nullptr;
    }

    int ttft = static_cast<int>(state->ttft_ms);
    return env->NewObject(statsClass, ctor, ttft, state->tokens_per_sec);
}
