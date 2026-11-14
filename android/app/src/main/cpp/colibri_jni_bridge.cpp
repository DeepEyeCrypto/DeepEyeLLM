// =============================================================================
// colibri_jni_bridge.cpp — DeepEyeLLM Native JNI Bridge for Colibri Inference Engine
// =============================================================================
// High-performance native bridge for Colibri LLM/MoE architectures:
//   - Qwen (3.6 / 3.8)
//   - GLM (GLM / GLM 5.3)
//   - Kimi (K3)
//   - OLMoE
//   - Inkling
// =============================================================================

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <chrono>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <algorithm>
#include <fstream>
#include <sched.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/resource.h>
#include <android/log.h>
#include <sys/stat.h>

#define LOG_TAG "DeepEyeLLM-Colibri"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
#include "edge_runtime.h"
#include "segment_runtime.h"
#include "edge_adapters.h"
#include "segment_adapters.h"
}

// ── Safe JNI UTF-8 & Byte Array String Conversion Helpers ──────────────────
// Prevents ART VM abort on invalid/partial UTF-8 bytes by using java.lang.String(byte[], "UTF-8")
static jstring safeNewStringUTF8(JNIEnv* env, const char* data, size_t len) {
    if (!data || len == 0) {
        return env->NewStringUTF("");
    }
    jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(len));
    if (!byteArray) return env->NewStringUTF("");
    env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));

    jclass strClass = env->FindClass("java/lang/String");
    if (!strClass) {
        env->DeleteLocalRef(byteArray);
        return env->NewStringUTF("");
    }
    jmethodID ctor = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jstring encoding = env->NewStringUTF("UTF-8");
    jstring result = static_cast<jstring>(env->NewObject(strClass, ctor, byteArray, encoding));

    env->DeleteLocalRef(encoding);
    env->DeleteLocalRef(byteArray);
    env->DeleteLocalRef(strClass);
    return result;
}

static void safeSendTokenChunk(JNIEnv* env, jobject callback, jmethodID onTokenBytesMethod, jmethodID onTokenMethod, const char* data, size_t len) {
    if (!callback || !env || !data || len == 0) return;

    if (onTokenBytesMethod) {
        jbyteArray jbytes = env->NewByteArray(static_cast<jsize>(len));
        if (jbytes) {
            env->SetByteArrayRegion(jbytes, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
            env->CallVoidMethod(callback, onTokenBytesMethod, jbytes);
            env->DeleteLocalRef(jbytes);
            return;
        }
    }

    if (onTokenMethod) {
        jstring jstr = safeNewStringUTF8(env, data, len);
        if (jstr) {
            env->CallVoidMethod(callback, onTokenMethod, jstr);
            env->DeleteLocalRef(jstr);
        }
    }
}

// ── Native Colibri Session Context ──────────────────────────────────────────
// Stored as a 64-bit jlong pointer in Kotlin ColibriEngine.
struct ColibriContext {
    std::string model_dir;
    std::string family;
    ColiEdgeEngine* edge = nullptr;
    ColiSegmentEngine* segment = nullptr;
    ColiSegmentSession* session = nullptr;
    ColiEdgeCapabilities edge_cap{};
    ColiSegmentCapabilities segment_cap{};

    uint32_t context_tokens = 2048;
    int n_threads = 4;
    int current_pos = 0;

    std::atomic<bool> is_aborted{false};
    std::mutex session_mutex;

    // Performance tracking
    double ttft_ms = 0.0;
    int tokens_generated = 0;
    double tokens_per_sec = 0.0;

    ~ColibriContext() {
        if (session) {
            coli_segment_session_destroy(session);
            session = nullptr;
        }
        if (segment) {
            coli_segment_engine_close(segment, nullptr, 0);
            segment = nullptr;
        }
        if (edge) {
            coli_edge_engine_close(edge);
            edge = nullptr;
        }
    }
};

// ── Global Adapter Registration ─────────────────────────────────────────────
static int register_all_colibri_adapters() {
    static bool registered = false;
    if (registered) return 0;

    int res = 0;
    res |= coli_glm_segment_adapter_register();
    res |= coli_glm53_segment_adapter_register();
    res |= coli_inkling_segment_adapter_register();
    res |= coli_kimi_segment_adapter_register();
    res |= coli_olmoe_segment_adapter_register();
    res |= coli_qwen36_segment_adapter_register();
    res |= coli_qwen38_segment_adapter_register();

    res |= coli_glm_edge_adapter_register();
    res |= coli_glm53_edge_adapter_register();
    res |= coli_inkling_edge_adapter_register();
    res |= coli_kimi_edge_adapter_register();
    res |= coli_olmoe_edge_adapter_register();
    res |= coli_qwen36_edge_adapter_register();
    res |= coli_qwen38_edge_adapter_register();

    registered = true;
    LOGI("All Colibri Edge/Segment adapters registered successfully");
    return res;
}

// ── Streaming UTF-8 Reassembly Helper ───────────────────────────────────────
static void drainCompleteUtf8(std::string& carry, const std::string& piece, std::string& complete) {
    if (!piece.empty()) {
        carry += piece;
    }
    if (carry.empty()) return;

    static const std::string kReplacement = "\xEF\xBF\xBD"; // U+FFFD

    size_t i = 0;
    const size_t n = carry.size();
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(carry[i]);
        size_t len;
        if (c < 0x80) {
            len = 1;
        } else if ((c & 0xE0) == 0xC0) {
            len = 2;
        } else if ((c & 0xF0) == 0xE0) {
            len = 3;
        } else if ((c & 0xF8) == 0xF0) {
            len = 4;
        } else {
            complete += kReplacement;
            i += 1;
            continue;
        }

        if (len == 1) {
            complete.push_back(static_cast<char>(c));
            i += 1;
            continue;
        }

        const size_t avail = n - i - 1;
        size_t have = 0;
        while (have < avail && have < len - 1) {
            if ((static_cast<unsigned char>(carry[i + 1 + have]) & 0xC0) != 0x80) break;
            have++;
        }

        if (have < len - 1) {
            if (have >= avail) {
                break; // Incomplete multi-byte sequence, keep in carry
            }
            complete += kReplacement;
            i += 1;
            continue;
        }

        complete.append(carry, i, len);
        i += len;
    }

    if (i > 0) {
        carry.erase(0, i);
    }
}

// ── Sampling Helper from Logits ─────────────────────────────────────────────
static int sample_token_from_logits(const float* logits, uint32_t vocab_size, float temperature, float top_p, int top_k) {
    if (vocab_size == 0 || !logits) return 0;

    if (temperature <= 0.01f) {
        int best = 0;
        float best_val = -1e30f;
        for (uint32_t i = 0; i < vocab_size; i++) {
            if (std::isfinite(logits[i]) && logits[i] > best_val) {
                best_val = logits[i];
                best = static_cast<int>(i);
            }
        }
        return best;
    }

    float inv_t = 1.0f / (temperature > 1e-4f ? temperature : 1e-4f);
    float max_l = -1e30f;
    for (uint32_t i = 0; i < vocab_size; i++) {
        if (std::isfinite(logits[i]) && logits[i] > max_l) {
            max_l = logits[i];
        }
    }

    struct TokScore {
        int id;
        float score;
    };
    std::vector<TokScore> scores(vocab_size);
    double sum_exp = 0.0;
    for (uint32_t i = 0; i < vocab_size; i++) {
        scores[i].id = static_cast<int>(i);
        if (std::isfinite(logits[i])) {
            float exp_val = expf((logits[i] - max_l) * inv_t);
            scores[i].score = exp_val;
            sum_exp += exp_val;
        } else {
            scores[i].score = 0.0f;
        }
    }

    if (sum_exp <= 0.0 || !std::isfinite(sum_exp)) {
        return 0;
    }

    for (uint32_t i = 0; i < vocab_size; i++) {
        scores[i].score /= static_cast<float>(sum_exp);
    }

    if (top_k > 0 && top_k < static_cast<int>(vocab_size)) {
        std::partial_sort(scores.begin(), scores.begin() + top_k, scores.end(),
            [](const TokScore& a, const TokScore& b) { return a.score > b.score; });
        scores.resize(top_k);
    } else {
        std::sort(scores.begin(), scores.end(),
            [](const TokScore& a, const TokScore& b) { return a.score > b.score; });
    }

    if (top_p > 0.0f && top_p < 1.0f) {
        float cum = 0.0f;
        size_t cutoff = scores.size();
        for (size_t i = 0; i < scores.size(); i++) {
            cum += scores[i].score;
            if (cum >= top_p) {
                cutoff = i + 1;
                break;
            }
        }
        scores.resize(cutoff);
    }

    float sub_sum = 0.0f;
    for (const auto& ts : scores) sub_sum += ts.score;
    if (sub_sum <= 0.0f) return scores[0].id;

    float r = (static_cast<float>(rand()) / static_cast<float>(RAND_MAX)) * sub_sum;
    float acc = 0.0f;
    for (const auto& ts : scores) {
        acc += ts.score;
        if (acc >= r) return ts.id;
    }
    return scores.back().id;
}

// ── Cancellation Check Callback ─────────────────────────────────────────────
static int coli_cancel_check(void* user_data) {
    if (!user_data) return 0;
    auto* ctx = reinterpret_cast<ColibriContext*>(user_data);
    return ctx->is_aborted.load(std::memory_order_relaxed) ? 1 : 0;
}

// ── Tokenization & Detokenization Internal Helpers ──────────────────────────
static std::vector<int32_t> tokenize_internal(ColibriContext* ctx, const std::string& text) {
    if (!ctx || !ctx->edge || text.empty()) return {};

    char error[512] = {0};
    size_t count = 0;
    if (coli_edge_tokenize(ctx->edge, text.c_str(), text.length(), nullptr, 0, &count, error, sizeof(error)) != 0 || count == 0) {
        LOGE("coli_edge_tokenize sizing failed: %s", error);
        return {};
    }

    std::vector<int32_t> ids(count);
    if (coli_edge_tokenize(ctx->edge, text.c_str(), text.length(), ids.data(), count, &count, error, sizeof(error)) != 0) {
        LOGE("coli_edge_tokenize encode failed: %s", error);
        return {};
    }
    ids.resize(count);
    return ids;
}

static std::string detokenize_internal(ColibriContext* ctx, const int32_t* ids, size_t count) {
    if (!ctx || !ctx->edge || !ids || count == 0) return "";

    char error[512] = {0};
    size_t bytes = 0;
    if (coli_edge_detokenize(ctx->edge, ids, count, nullptr, 0, &bytes, error, sizeof(error)) != 0) {
        LOGE("coli_edge_detokenize sizing failed: %s", error);
        return "";
    }
    if (bytes == 0) return "";

    std::vector<char> buf(bytes + 1, 0);
    if (coli_edge_detokenize(ctx->edge, ids, count, buf.data(), buf.size(), &bytes, error, sizeof(error)) != 0) {
        LOGE("coli_edge_detokenize decode failed: %s", error);
        return "";
    }
    return std::string(buf.data(), bytes);
}

// ── CPU Affinity & Thread Priority Helper ───────────────────────────────────
static std::vector<int> detectBigCores() {
    std::vector<std::pair<int, unsigned long>> core_freqs;
    for (int i = 0; i < 16; i++) {
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/cpufreq/cpuinfo_max_freq";
        std::ifstream f(path);
        if (f.is_open()) {
            unsigned long freq = 0;
            if (f >> freq) {
                core_freqs.push_back({i, freq});
            }
        }
    }
    if (core_freqs.empty()) {
        int num_cpus = sysconf(_SC_NPROCESSORS_CONF);
        if (num_cpus >= 8) return {6, 7};
        if (num_cpus >= 4) return {2, 3};
        return {0};
    }
    std::sort(core_freqs.begin(), core_freqs.end(), [](auto& a, auto& b) {
        return a.second > b.second;
    });
    unsigned long max_freq = core_freqs.front().second;
    std::vector<int> big_cores;
    for (auto& p : core_freqs) {
        if (p.second >= max_freq * 0.85) {
            big_cores.push_back(p.first);
        }
    }
    return big_cores.empty() ? std::vector<int>{core_freqs.front().first} : big_cores;
}

static void applyPerformanceThreadAffinity() {
    auto big_cores = detectBigCores();
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int core : big_cores) {
        CPU_SET(core, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    setpriority(PRIO_PROCESS, 0, -20);
    LOGI("Applied ARM big-core affinity mask to %zu performance cores (elevated priority)", big_cores.size());
}

// =============================================================================
// JNI Exported Methods for com.deepeye.agent.domain.engine.ColibriEngine
// =============================================================================

extern "C" {

// =============================================================================
// JNI Implementation Logic
// =============================================================================

static jlong initModelInternal(JNIEnv* env, jstring jmodel_dir, jstring jfamily, jint max_ctx, jint threads) {
    register_all_colibri_adapters();
    applyPerformanceThreadAffinity();

    const char* model_dir_c = jmodel_dir ? env->GetStringUTFChars(jmodel_dir, nullptr) : nullptr;
    const char* family_c = jfamily ? env->GetStringUTFChars(jfamily, nullptr) : nullptr;

    std::string model_dir = model_dir_c ? model_dir_c : "";
    std::string family = (family_c && strlen(family_c) > 0) ? family_c : "auto";

    if (model_dir_c) env->ReleaseStringUTFChars(jmodel_dir, model_dir_c);
    if (family_c) env->ReleaseStringUTFChars(jfamily, family_c);

    if (model_dir.empty()) {
        LOGE("Cannot initialize Colibri: model_dir is empty");
        return 0;
    }

    struct stat st;
    if (stat(model_dir.c_str(), &st) != 0) {
        LOGE("Cannot initialize Colibri: path '%s' does not exist", model_dir.c_str());
        return 0;
    }

    std::string actual_dir = model_dir;
    if (S_ISREG(st.st_mode)) {
        size_t last_slash = model_dir.find_last_of('/');
        if (last_slash != std::string::npos) {
            actual_dir = model_dir.substr(0, last_slash);
        }
    }

    // Auto-detect model family from directory contents if needed
    if (family == "auto" || family.empty()) {
        if (access((actual_dir + "/qwen38_core.json").c_str(), F_OK) == 0) {
            family = "qwen38";
        } else if (access((actual_dir + "/qwen36_core.json").c_str(), F_OK) == 0) {
            family = "qwen36";
        } else if (access((actual_dir + "/glm53_core.json").c_str(), F_OK) == 0) {
            family = "glm53";
        } else if (access((actual_dir + "/olmoe_core.json").c_str(), F_OK) == 0) {
            family = "olmoe";
        } else if (access((actual_dir + "/config.json").c_str(), F_OK) == 0) {
            family = "qwen38";
        } else {
            family = "qwen38";
        }
    }

    LOGI("Initializing Colibri Engine: family='%s', dir='%s' (actual='%s'), ctx=%d, threads=%d",
         family.c_str(), model_dir.c_str(), actual_dir.c_str(), max_ctx, threads);

    auto ctx = std::make_unique<ColibriContext>();
    ctx->model_dir = actual_dir;
    ctx->family = family;
    ctx->context_tokens = static_cast<uint32_t>(max_ctx > 0 ? max_ctx : 2048);

    int num_cpus = sysconf(_SC_NPROCESSORS_CONF);
    int worker_threads = std::max(1, (threads > 0 ? threads : num_cpus - 2));
    // Enforce thread affinity / capping to 4 Performance Cores
    if (worker_threads > 4) {
        worker_threads = 4;
    }
    ctx->n_threads = worker_threads;

    if (ctx->context_tokens > 4096) {
        LOGW("Requested context tokens %u exceeds safe bound for mobile -- capping at 4096.", ctx->context_tokens);
        ctx->context_tokens = 4096;
    }

    setenv("COLI_MMAP", "1", 1);
    setenv("COLI_MAP_EXPERTS", "1", 1);
    setenv("K3_MMAP", "1", 1);
    setenv("TRUNK_RESIDENT_LAYERS", "0", 1);
    setenv("COLI_DIRECT", "0", 1);
    setenv("MLOCK", "1", 1);
    setenv("COLI_MLOCK", "1", 1);
    setenv("OMP_NUM_THREADS", std::to_string(ctx->n_threads).c_str(), 1);

    char error[512] = {0};

    // 1. Open Edge Engine
    ColiEdgeEngineOptions edge_opts = {
        sizeof(edge_opts),
        ctx->model_dir.c_str(),
        0, // memory_limit_bytes
        0, // backend_mask
        nullptr, 0, {0, 0, 0}
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

    // 2. Open Segment Engine (layers 0 .. num_layers)
    ColiSegmentEngineOptions seg_opts = {
        sizeof(seg_opts),
        ctx->model_dir.c_str(),
        0,
        ctx->edge_cap.num_layers,
        ctx->context_tokens,
        0, 0, 0, nullptr, 0
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

    // 3. Create Segment Session
    ColiSegmentSessionOptions sess_opts = {
        sizeof(sess_opts),
        ctx->context_tokens,
        0, {0, 0, 0, 0}
    };
    if (coli_segment_session_create(ctx->segment, &sess_opts, &ctx->session, error, sizeof(error)) != 0) {
        LOGE("Failed to create Colibri Segment Session: %s", error);
        return 0;
    }

    LOGI("Colibri Engine ready: %s (vocab: %u, layers: %u, width: %u)",
         ctx->family.c_str(), ctx->edge_cap.vocab_size, ctx->edge_cap.num_layers, ctx->edge_cap.state_width);

    return reinterpret_cast<jlong>(ctx.release());
}
// ─────────────────────────────────────────────────────────────────────────────
// nativeInitModel Overloads
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jlong JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeInitModel__Ljava_lang_String_2Ljava_lang_String_2II(
    JNIEnv* env, jobject /*thiz*/, jstring jmodel_dir, jstring jfamily, jint max_ctx, jint threads) {
    return initModelInternal(env, jmodel_dir, jfamily, max_ctx, threads);
}

JNIEXPORT jlong JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeInitModel__Ljava_lang_String_2II(
    JNIEnv* env, jobject /*thiz*/, jstring jmodel_dir, jint max_ctx, jint threads) {
    return initModelInternal(env, jmodel_dir, nullptr, max_ctx, threads);
}

JNIEXPORT jlong JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeInitModel(
    JNIEnv* env, jobject /*thiz*/, jstring jmodel_dir, jstring jfamily, jint max_ctx, jint threads) {
    return initModelInternal(env, jmodel_dir, jfamily, max_ctx, threads);
}


// ─────────────────────────────────────────────────────────────────────────────
// nativeTokenize — Tokenizes a UTF-8 string into an int array of token IDs
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jintArray JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeTokenize(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jtext) {

    if (handle == 0 || !jtext) return env->NewIntArray(0);
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    const char* text_c = env->GetStringUTFChars(jtext, nullptr);
    std::string text(text_c ? text_c : "");
    if (text_c) env->ReleaseStringUTFChars(jtext, text_c);

    std::vector<int32_t> tokens = tokenize_internal(ctx, text);
    jintArray result = env->NewIntArray(static_cast<jsize>(tokens.size()));
    if (!tokens.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(tokens.size()), reinterpret_cast<const jint*>(tokens.data()));
    }
    return result;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeDetokenize — Decodes an int array of token IDs into a UTF-8 string
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeDetokenize(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jintArray jtoken_ids) {

    if (handle == 0 || !jtoken_ids) return safeNewStringUTF8(env, "", 0);
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    jsize len = env->GetArrayLength(jtoken_ids);
    if (len == 0) return safeNewStringUTF8(env, "", 0);

    std::vector<int32_t> ids(len);
    env->GetIntArrayRegion(jtoken_ids, 0, len, reinterpret_cast<jint*>(ids.data()));

    std::string text = detokenize_internal(ctx, ids.data(), ids.size());
    return safeNewStringUTF8(env, text.data(), text.size());
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGenerateNextToken — Single-step next token generation
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jint JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeGenerateNextToken(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jintArray jtokens,
    jint current_pos, jfloat temperature, jfloat top_p) {

    if (handle == 0 || !jtokens) return -1;
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    std::lock_guard<std::mutex> lock(ctx->session_mutex);
    jsize count = env->GetArrayLength(jtokens);
    if (count == 0) return -1;

    std::vector<int32_t> tokens(count);
    env->GetIntArrayRegion(jtokens, 0, count, reinterpret_cast<jint*>(tokens.data()));

    size_t row_bytes = static_cast<size_t>(ctx->edge_cap.state_width) * sizeof(float);
    std::vector<float> input(count * ctx->edge_cap.state_width);
    std::vector<float> output(count * ctx->edge_cap.state_width);
    char error[512] = {0};

    ColiEdgeEmbedRequest embed = {
        sizeof(embed),
        static_cast<uint32_t>(count),
        tokens.data(),
        static_cast<size_t>(count),
        input.data(),
        input.size() * sizeof(float),
        coli_cancel_check,
        ctx,
        {0, 0, 0}
    };
    if (coli_edge_embed(ctx->edge, &embed, error, sizeof(error)) != 0) {
        LOGE("coli_edge_embed failed: %s", error);
        return -1;
    }

    ColiSegmentRunRequest run = {
        sizeof(run),
        static_cast<uint32_t>(count),
        static_cast<uint64_t>(current_pos),
        tokens.data(),
        static_cast<size_t>(count),
        input.data(),
        input.size() * sizeof(float),
        output.data(),
        output.size() * sizeof(float),
        coli_cancel_check,
        ctx,
        {0, 0, 0, 0}
    };
    if (coli_segment_run(ctx->session, &run, error, sizeof(error)) != 0) {
        LOGE("coli_segment_run failed: %s", error);
        return -1;
    }

    float* last_hidden = output.data() + (count - 1) * ctx->edge_cap.state_width;
    int32_t predicted = -1;

    if (temperature <= 0.01f) {
        float score = 0.0f;
        ColiEdgeSelectRequest select = {
            sizeof(select),
            1,
            last_hidden,
            row_bytes,
            &predicted,
            1,
            &score,
            1,
            coli_cancel_check,
            ctx,
            {0, 0, 0}
        };
        if (coli_edge_select(ctx->edge, &select, error, sizeof(error)) != 0) {
            LOGE("coli_edge_select failed: %s", error);
            return -1;
        }
    } else {
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
            LOGE("coli_edge_logits failed: %s", error);
            return -1;
        }
        predicted = sample_token_from_logits(logits.data(), ctx->edge_cap.vocab_size, temperature, top_p, 40);
    }

    return predicted;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGenerateResponseStream — Full streaming generation via JNI callback
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeGenerateResponseStream(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jprompt, jint max_tokens,
    jfloat temperature, jfloat top_p, jobject callback) {

    if (handle == 0 || !callback) return;
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    ctx->is_aborted.store(false, std::memory_order_relaxed);
    applyPerformanceThreadAffinity();

    // Resolve callback method IDs safely
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenBytesMethod = env->GetMethodID(cbClass, "onTokenBytes", "([B)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        onTokenBytesMethod = nullptr;
    }
    jmethodID onTokenMethod = env->GetMethodID(cbClass, "onTokenGenerated", "(Ljava/lang/String;)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        onTokenMethod = nullptr;
    }
    jmethodID onCompleteMethod = env->GetMethodID(cbClass, "onGenerationComplete", "()V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        onCompleteMethod = nullptr;
    }
    jmethodID onErrorMethod = env->GetMethodID(cbClass, "onGenerationError", "(Ljava/lang/String;)V");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        onErrorMethod = nullptr;
    }
    env->DeleteLocalRef(cbClass);

    if (!onTokenMethod && !onTokenBytesMethod) {
        LOGE("FATAL: neither onTokenGenerated nor onTokenBytes method found on callback");
        return;
    }

    const char* prompt_c = jprompt ? env->GetStringUTFChars(jprompt, nullptr) : nullptr;
    std::string prompt(prompt_c ? prompt_c : "");
    if (prompt_c) env->ReleaseStringUTFChars(jprompt, prompt_c);

    std::lock_guard<std::mutex> lock(ctx->session_mutex);

    // 1. Tokenize prompt
    std::vector<int32_t> prompt_tokens = tokenize_internal(ctx, prompt);
    if (prompt_tokens.empty()) {
        if (onErrorMethod) {
            jstring errStr = safeNewStringUTF8(env, "Failed to tokenize prompt", 25);
            env->CallVoidMethod(callback, onErrorMethod, errStr);
            env->DeleteLocalRef(errStr);
        }
        return;
    }

    size_t prompt_count = prompt_tokens.size();
    if (prompt_count >= ctx->context_tokens) {
        if (onErrorMethod) {
            jstring errStr = safeNewStringUTF8(env, "Prompt exceeds context token capacity", 37);
            env->CallVoidMethod(callback, onErrorMethod, errStr);
            env->DeleteLocalRef(errStr);
        }
        return;
    }

    auto t_start = std::chrono::steady_clock::now();
    size_t state_width = ctx->edge_cap.state_width;
    size_t row_bytes = state_width * sizeof(float);
    char error[512] = {0};

    // 2. Prefill prompt activations
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
        if (onErrorMethod) {
            jstring errStr = safeNewStringUTF8(env, error, strlen(error));
            env->CallVoidMethod(callback, onErrorMethod, errStr);
            env->DeleteLocalRef(errStr);
        }
        return;
    }

    ColiSegmentRunRequest run = {
        sizeof(run),
        static_cast<uint32_t>(prompt_count),
        0, // Start position = 0
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
        if (onErrorMethod) {
            jstring errStr = safeNewStringUTF8(env, error, strlen(error));
            env->CallVoidMethod(callback, onErrorMethod, errStr);
            env->DeleteLocalRef(errStr);
        }
        return;
    }

    auto t_prefill_end = std::chrono::steady_clock::now();
    ctx->ttft_ms = std::chrono::duration<double, std::milli>(t_prefill_end - t_start).count();

    // 3. First generated token from last prompt position
    float* last_hidden = output.data() + (prompt_count - 1) * state_width;
    int32_t current_token = -1;

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
            if (onErrorMethod) {
                jstring errStr = safeNewStringUTF8(env, error, strlen(error));
                env->CallVoidMethod(callback, onErrorMethod, errStr);
                env->DeleteLocalRef(errStr);
            }
            return;
        }
    } else {
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
            if (onErrorMethod) {
                jstring errStr = safeNewStringUTF8(env, error, strlen(error));
                env->CallVoidMethod(callback, onErrorMethod, errStr);
                env->DeleteLocalRef(errStr);
            }
            return;
        }
        current_token = sample_token_from_logits(logits.data(), ctx->edge_cap.vocab_size, temperature, top_p, 40);
    }

    std::string carry;
    std::string complete_chunk;
    int n_generated = 0;
    int max_gen = max_tokens > 0 ? max_tokens : 512;
    uint32_t current_pos = static_cast<uint32_t>(prompt_count);

    // Reuse 1-row activation buffers for decode loop
    std::vector<float> step_in(state_width);
    std::vector<float> step_out(state_width);

    while (n_generated < max_gen && current_pos < ctx->context_tokens) {
        if (ctx->is_aborted.load(std::memory_order_relaxed)) {
            LOGI("Generation cancelled after %d tokens", n_generated);
            break;
        }

        // Check for EOS
        if (current_token == ctx->edge_cap.eos_token_id) {
            break;
        }

        // Detokenize current token
        std::string piece = detokenize_internal(ctx, &current_token, 1);
        if (piece == "<|im_end|>" || piece == "<|endoftext|>" || piece == "</s>" ||
            piece == "<end_of_turn>" || piece == "<|eot_id|>") {
            break;
        }

        drainCompleteUtf8(carry, piece, complete_chunk);
        if (!complete_chunk.empty()) {
            if (onTokenBytesMethod) {
                safeSendTokenChunk(env, callback, onTokenBytesMethod, nullptr, complete_chunk.data(), complete_chunk.size());
            } else if (onTokenMethod) {
                safeSendTokenChunk(env, callback, nullptr, onTokenMethod, complete_chunk.data(), complete_chunk.size());
            }
            complete_chunk.clear();
        }

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGW("Exception in callback, stopping Colibri generation");
            break;
        }

        n_generated++;

        // Autoregressive forward step for next token
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
                break;
            }
            current_token = sample_token_from_logits(logits.data(), ctx->edge_cap.vocab_size, temperature, top_p, 40);
        }
    }

    // Flush any remaining characters in carry
    if (!carry.empty()) {
        if (onTokenBytesMethod) {
            safeSendTokenChunk(env, callback, onTokenBytesMethod, nullptr, carry.data(), carry.size());
        } else if (onTokenMethod) {
            safeSendTokenChunk(env, callback, nullptr, onTokenMethod, carry.data(), carry.size());
        }
        carry.clear();
    }

    auto t_end = std::chrono::steady_clock::now();
    double total_sec = std::chrono::duration<double>(t_end - t_start).count();
    ctx->tokens_generated = n_generated;
    ctx->tokens_per_sec = total_sec > 0.0 ? (n_generated / total_sec) : 0.0;

    LOGI("Generation finished: %d tokens in %.2fs (%.2f tok/s, TTFT: %.1fms)",
         n_generated, total_sec, ctx->tokens_per_sec, ctx->ttft_ms);

    if (onCompleteMethod) {
        env->CallVoidMethod(callback, onCompleteMethod);
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// nativeGenerateResponse — Synchronous full response generation
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeGenerateResponse(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jprompt, jint max_tokens,
    jfloat temperature, jfloat top_p) {

    if (handle == 0) return env->NewStringUTF("");
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    ctx->is_aborted.store(false, std::memory_order_relaxed);
    applyPerformanceThreadAffinity();

    const char* prompt_c = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_c ? prompt_c : "");
    if (prompt_c) env->ReleaseStringUTFChars(jprompt, prompt_c);

    std::lock_guard<std::mutex> lock(ctx->session_mutex);

    std::vector<int32_t> prompt_tokens = tokenize_internal(ctx, prompt);
    if (prompt_tokens.empty()) return env->NewStringUTF("");

    size_t prompt_count = prompt_tokens.size();
    if (prompt_count >= ctx->context_tokens) return env->NewStringUTF("");

    size_t state_width = ctx->edge_cap.state_width;
    size_t row_bytes = state_width * sizeof(float);
    char error[512] = {0};

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
        return env->NewStringUTF("");
    }

    ColiSegmentRunRequest run = {
        sizeof(run),
        static_cast<uint32_t>(prompt_count),
        0,
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
        return env->NewStringUTF("");
    }

    float* last_hidden = output.data() + (prompt_count - 1) * state_width;
    int32_t current_token = -1;

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
        if (coli_edge_select(ctx->edge, &select, error, sizeof(error)) != 0) return env->NewStringUTF("");
    } else {
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
        if (coli_edge_logits(ctx->edge, &logits_req, error, sizeof(error)) != 0) return env->NewStringUTF("");
        current_token = sample_token_from_logits(logits.data(), ctx->edge_cap.vocab_size, temperature, top_p, 40);
    }

    std::string result_text;
    std::string carry;
    int n_generated = 0;
    int max_gen = max_tokens > 0 ? max_tokens : 512;
    uint32_t current_pos = static_cast<uint32_t>(prompt_count);

    std::vector<float> step_in(state_width);
    std::vector<float> step_out(state_width);

    while (n_generated < max_gen && current_pos < ctx->context_tokens) {
        if (ctx->is_aborted.load(std::memory_order_relaxed)) break;
        if (current_token == ctx->edge_cap.eos_token_id) break;

        std::string piece = detokenize_internal(ctx, &current_token, 1);
        if (piece == "<|im_end|>" || piece == "<|endoftext|>" || piece == "</s>" ||
            piece == "<end_of_turn>" || piece == "<|eot_id|>") {
            break;
        }

        drainCompleteUtf8(carry, piece, result_text);
        n_generated++;

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
        if (coli_edge_embed(ctx->edge, &step_embed, error, sizeof(error)) != 0) break;

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
        if (coli_segment_run(ctx->session, &step_run, error, sizeof(error)) != 0) break;

        current_pos++;

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
            if (coli_edge_select(ctx->edge, &select, error, sizeof(error)) != 0) break;
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
            if (coli_edge_logits(ctx->edge, &logits_req, error, sizeof(error)) != 0) break;
            current_token = sample_token_from_logits(logits.data(), ctx->edge_cap.vocab_size, temperature, top_p, 40);
        }
    }

    if (!carry.empty()) {
        result_text.append(carry);
    }

    return safeNewStringUTF8(env, result_text.data(), result_text.size());
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeAbortGeneration — Signals current generation loop to immediately exit
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeAbortGeneration(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {

    if (handle == 0) return;
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);
    ctx->is_aborted.store(true, std::memory_order_relaxed);
    LOGI("Aborted active Colibri generation");
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeResetSession — Clears session state and recreates KV cache for new context
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jboolean JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeResetSession(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {

    if (handle == 0) return JNI_FALSE;
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    std::lock_guard<std::mutex> lock(ctx->session_mutex);
    if (ctx->session) {
        coli_segment_session_destroy(ctx->session);
        ctx->session = nullptr;
    }

    char error[512] = {0};
    ColiSegmentSessionOptions sess_opts = {
        sizeof(sess_opts),
        ctx->context_tokens,
        0, {0, 0, 0, 0}
    };
    if (coli_segment_session_create(ctx->segment, &sess_opts, &ctx->session, error, sizeof(error)) != 0) {
        LOGE("Failed to recreate Colibri Segment Session: %s", error);
        return JNI_FALSE;
    }

    ctx->current_pos = 0;
    LOGI("Reset Colibri Segment Session successfully");
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeReleaseModel — Releases all resources allocated for the engine
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeReleaseModel(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {

    if (handle == 0) return;
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);
    LOGI("Releasing Colibri engine resources (handle: %p)", ctx);
    delete ctx;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGetModelInfo — Returns model metadata (family, vocab size, layers, width)
// ─────────────────────────────────────────────────────────────────────────────
JNIEXPORT jstring JNICALL
Java_com_deepeye_agent_domain_engine_ColibriEngine_nativeGetModelInfo(
    JNIEnv* env, jobject /*thiz*/, jlong handle) {

    if (handle == 0) return safeNewStringUTF8(env, "{}", 2);
    auto* ctx = reinterpret_cast<ColibriContext*>(handle);

    char info[512];
    snprintf(info, sizeof(info),
             "{\"family\":\"%s\",\"vocab_size\":%u,\"num_layers\":%u,\"state_width\":%u,\"context\":%u}",
             ctx->family.c_str(),
             ctx->edge_cap.vocab_size,
             ctx->edge_cap.num_layers,
             ctx->edge_cap.state_width,
             ctx->context_tokens);

    return safeNewStringUTF8(env, info, strlen(info));
}

} // extern "C"


