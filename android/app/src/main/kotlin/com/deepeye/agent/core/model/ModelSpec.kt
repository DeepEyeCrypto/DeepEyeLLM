package com.deepeye.agent.core.model

/**
 * Capabilities that a model can support.
 * Used for filtering and routing inference requests.
 */
enum class ModelCapability {
    CHAT,
    VISION,
    AUDIO,
    CODE,
    FUNCTION_CALLING,
    EMBEDDING,
}

/**
 * Backend runtime the model requires.
 */
enum class ModelBackend(val displayName: String) {
    LITERT("Google LiteRT / MediaPipe"),
    GGUF_LLAMA_CPP("GGUF via llama.cpp"),
    ONNX("ONNX Runtime"),
    MNN("Alibaba MNN"),
}

/**
 * Quantization level of the model weights.
 * Lower precision = smaller file, faster inference, slightly lower quality.
 */
enum class Quantization(val label: String, val bitsPerWeight: Float) {
    F16("FP16", 16f),
    Q8_0("INT8", 8f),
    Q6_K("6-bit K-Quant", 6f),
    Q5_K_M("5-bit K-Quant Medium", 5f),
    Q4_K_M("4-bit K-Quant Medium", 4f),
    Q4_0("4-bit Naive", 4f),
    Q3_K_M("3-bit K-Quant Medium", 3f),
    Q2_K("2-bit K-Quant", 2f),
}

/**
 * Full specification of a model available on the device or in the catalog.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val family: String,                  // e.g., "Gemma", "Qwen", "Llama", "Phi"
    val parameterCount: String,          // e.g., "2B", "4B", "7B"
    val backend: ModelBackend,
    val quantization: Quantization,
    val sizeBytes: Long,                 // on-disk size
    val requiredRamBytes: Long,          // estimated RAM needed at runtime
    val capabilities: Set<ModelCapability>,
    val fileName: String,                // e.g., "gemma-4-2b-q4_k_m.bin"
    val isDownloaded: Boolean = false,
    val downloadUrl: String? = null,
    val sha256Hash: String? = null,
) {
    val sizeMb: Long get() = sizeBytes / (1024 * 1024)
    val requiredRamMb: Long get() = requiredRamBytes / (1024 * 1024)

    companion object {
        /**
         * Pre-defined catalog of known models.
         */
        val CATALOG = listOf(
            // --- Ultra-Fast 100+ Tokens/Sec Models ---
            ModelSpec(
                id = "qwen2.5-0.5b-instruct-q4km",
                name = "Qwen 2.5 0.5B Turbo",
                family = "Qwen",
                parameterCount = "0.5B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 398_000_000L,
                requiredRamBytes = 600_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            ),
            ModelSpec(
                id = "smollm2-135m-q4km",
                name = "SmolLM2 135M Instant",
                family = "SmolLM",
                parameterCount = "135M",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 95_000_000L,
                requiredRamBytes = 200_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                fileName = "smollm2-135m-instruct-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q4_k_m.gguf",
            ),

            // --- Gemma 4 ---
            ModelSpec(
                id = "gemma-4-2b-q4km",
                name = "Gemma 4 2B",
                family = "Gemma",
                parameterCount = "2B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 1_630_000_000L,
                requiredRamBytes = 2_500_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION, ModelCapability.CODE),
                fileName = "gemma4-2b-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            ),
            ModelSpec(
                id = "gemma-4-4b-q4km",
                name = "Gemma 4 4B",
                family = "Gemma",
                parameterCount = "4B",
                backend = ModelBackend.LITERT,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 2_800_000_000L,
                requiredRamBytes = 4_500_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION, ModelCapability.AUDIO, ModelCapability.CODE),
                fileName = "gemma-2b-it-cpu-int4.bin",
                downloadUrl = "https://huggingface.co/litert-models/gemma-2b-it/resolve/main/gemma-2b-it-cpu-int4.bin",
            ),

            // --- Qwen 3 ---
            ModelSpec(
                id = "qwen3-1.7b-q4km",
                name = "Qwen 3 1.7B",
                family = "Qwen",
                parameterCount = "1.7B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 1_200_000_000L,
                requiredRamBytes = 2_000_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "qwen3-1.7b-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen1.5-1.8B-Chat-GGUF/resolve/main/qwen1_5-1_8b-chat-q4_k_m.gguf",
            ),
            ModelSpec(
                id = "qwen3-4b-q4km",
                name = "Qwen 3 4B",
                family = "Qwen",
                parameterCount = "4B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 2_600_000_000L,
                requiredRamBytes = 4_200_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "qwen3-4b-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen1.5-4B-Chat-GGUF/resolve/main/qwen1_5-4b-chat-q4_k_m.gguf",
            ),

            // --- Llama 4 Scout ---
            ModelSpec(
                id = "llama4-scout-1b-q8",
                name = "Llama 4 Scout 1B",
                family = "Llama",
                parameterCount = "1B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q8_0,
                sizeBytes = 1_100_000_000L,
                requiredRamBytes = 1_800_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE),
                fileName = "llama4-scout-1b-q8_0.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf",
            ),

            // --- Phi-4 Mini ---
            ModelSpec(
                id = "phi4-mini-3.8b-q4km",
                name = "Phi-4 Mini 3.8B",
                family = "Phi",
                parameterCount = "3.8B",
                backend = ModelBackend.ONNX,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 2_400_000_000L,
                requiredRamBytes = 3_800_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "phi-4-mini-3.8b-q4_k_m.onnx",
                downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
            ),

            // --- FunctionGemma (tool routing) ---
            ModelSpec(
                id = "function-gemma-270m",
                name = "FunctionGemma 270M",
                family = "Gemma",
                parameterCount = "270M",
                backend = ModelBackend.LITERT,
                quantization = Quantization.Q8_0,
                sizeBytes = 300_000_000L,
                requiredRamBytes = 500_000_000L,
                capabilities = setOf(ModelCapability.FUNCTION_CALLING),
                fileName = "gemma-2b-it-cpu-int4.bin",
                downloadUrl = "https://huggingface.co/litert-models/gemma-2b-it/resolve/main/gemma-2b-it-cpu-int4.bin",
            ),

            // --- Embedding model ---
            ModelSpec(
                id = "gte-small-384",
                name = "GTE Small (384d)",
                family = "GTE",
                parameterCount = "33M",
                backend = ModelBackend.ONNX,
                quantization = Quantization.F16,
                sizeBytes = 70_000_000L,
                requiredRamBytes = 150_000_000L,
                capabilities = setOf(ModelCapability.EMBEDDING),
                fileName = "gte-small-384.onnx",
                downloadUrl = "https://huggingface.co/Supabase/gte-small/resolve/main/onnx/model.onnx",
            ),

            // --- Nous Research Hermes 3 ---
            ModelSpec(
                id = "hermes-3-3b-q4km",
                name = "Hermes 3 3B (Nous Research)",
                family = "Hermes",
                parameterCount = "3B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 2_100_000_000L,
                requiredRamBytes = 3_200_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "hermes-3-3b-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF/resolve/main/Hermes-3-Llama-3.2-3B.Q4_K_M.gguf",
            ),
            ModelSpec(
                id = "hermes-3-8b-q4km",
                name = "Hermes 3 8B (Nous Research)",
                family = "Hermes",
                parameterCount = "8B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 4_900_000_000L,
                requiredRamBytes = 6_500_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "hermes-3-8b-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.1-8B-GGUF/resolve/main/Hermes-3-Llama-3.1-8B.Q4_K_M.gguf",
            ),

            // --- DeepSeek V4 Flash (MoE, ~290B total params) ---
            // DeepSeek-V4-Flash is a large MoE (num_experts_per_tok: 6, DeepseekV4 arch).
            // This is the smallest standalone single-file GGUF available (~6 GB, an antirez
            // mixed/REAP "DSpark-support" build). It needs ≥ ~11 GB free RAM to load, so the app's
            // RAM filter keeps it out of the picker on phones (~8 GB) and only surfaces it on
            // desktop/large-RAM devices. NOTE: quantization is a mixed precision build, not a
            // clean Q4_K_M — Q4_K_M is the nearest available enum label.
            ModelSpec(
                id = "deepseek-v4-flash-dspark-support",
                name = "DeepSeek V4 Flash",
                family = "DeepSeek",
                parameterCount = "290B",
                backend = ModelBackend.GGUF_LLAMA_CPP,
                quantization = Quantization.Q4_K_M,
                sizeBytes = 5_989_114_272L,
                requiredRamBytes = 12_000_000_000L,
                capabilities = setOf(ModelCapability.CHAT, ModelCapability.CODE, ModelCapability.FUNCTION_CALLING),
                fileName = "DeepSeek-V4-Flash-DSpark-support.gguf",
                downloadUrl = "https://huggingface.co/antirez/deepseek-v4-gguf/resolve/main/DeepSeek-V4-Flash-DSpark-support.gguf",
            ),
        )
    }
}
