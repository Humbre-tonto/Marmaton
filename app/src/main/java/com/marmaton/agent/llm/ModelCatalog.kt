package com.marmaton.agent.llm

/**
 * A single downloadable on-device model. The catalog carries two families:
 *  - MediaPipe LLM Inference `.task` bundles, loaded by [LocalFileBackend].
 *  - llama.cpp `.gguf` weights, loaded by the native [GgufBackend]. These are ungated and
 *    are the recommended path — Qwen 2.5 Instruct is a much stronger JSON-action reasoner
 *    per byte than the tiny gated Gemma `.task` builds.
 *
 * `.gguf` entries are routed to [GgufBackend] by [BackendFactory] on file extension.
 *
 * Most Gemma `.task` weights are license-gated on Hugging Face, so [gated] models require the
 * user to accept the model's license once on the model page and supply a Hugging Face access
 * token (stored via [SecurePreferences.getHuggingFaceToken]). The token is sent only as a Bearer
 * `Authorization` header to huggingface.co and is never logged or sent anywhere else.
 */
data class CatalogModel(
    val id: String,
    val name: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
    val sizeLabel: String,
    val description: String,
    val gated: Boolean,
    /** The model's Hugging Face page, where the user accepts the license for gated models. */
    val licenseUrl: String? = null,
    val sha256: String? = null
)

object ModelCatalog {

    val models: List<CatalogModel> = listOf(
        // ---- Recommended GGUF models (ungated, run on the native llama.cpp engine) ----
        // Ordered from the best all-round phone pick down to the lightest. All are ungated, so
        // no Hugging Face login/token is required.
        CatalogModel(
            id = "qwen2.5-1.5b-it-gguf",
            name = "Qwen 2.5 1.5B · Instruct GGUF (q4_k_m) · Recommended",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
            approxSizeBytes = 1_120_000_000L,
            sizeLabel = "~1.1 GB",
            description = "Recommended for most phones (4 GB+ RAM). Alibaba's Qwen 2.5 is a strong instruction follower and reliably emits the JSON actions the agent needs — the best balance of quality and speed on-device.",
            gated = false
        ),
        CatalogModel(
            id = "qwen2.5-0.5b-it-gguf",
            name = "Qwen 2.5 0.5B · Instruct GGUF (q4_k_m) · Lightweight",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
            approxSizeBytes = 398_000_000L,
            sizeLabel = "~398 MB",
            description = "For older or low-RAM phones. Very fast, smaller download; weaker reasoning than the 1.5B but still usable for simple goals.",
            gated = false
        ),
        CatalogModel(
            id = "qwen2.5-3b-it-gguf",
            name = "Qwen 2.5 3B · Instruct GGUF (q4_k_m) · Best quality",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf?download=true",
            approxSizeBytes = 1_930_000_000L,
            sizeLabel = "~1.9 GB",
            description = "Best on-device quality, for higher-end phones (6 GB+ RAM). Noticeably better at multi-step reasoning; slower per step and a larger download.",
            gated = false
        ),
        CatalogModel(
            id = "smollm2-360m-it-gguf",
            name = "SmolLM2 360M · Instruct GGUF (q8_0) · Ultralight",
            fileName = "smollm2-360m-instruct-q8_0.gguf",
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf?download=true",
            approxSizeBytes = 380_000_000L,
            sizeLabel = "~380 MB",
            description = "The lightest option from Hugging Face. Fine for a quick test on very limited devices; too small to drive the agent reliably.",
            gated = false
        ),
        // ---- Coder GGUF models (ungated; tuned for writing and explaining code in Chat) ----
        CatalogModel(
            id = "qwen2.5-coder-1.5b-it-gguf",
            name = "Qwen 2.5 Coder 1.5B · Instruct GGUF (q4_k_m) · Coding",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true",
            approxSizeBytes = 1_120_000_000L,
            sizeLabel = "~1.1 GB",
            description = "Best small coding model for most phones (4 GB+ RAM). Great for writing and explaining short snippets in Chat. Not tuned for driving the on-screen agent — pick a Qwen Instruct model for that.",
            gated = false
        ),
        CatalogModel(
            id = "qwen2.5-coder-3b-it-gguf",
            name = "Qwen 2.5 Coder 3B · Instruct GGUF (q4_k_m) · Best coding",
            fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf?download=true",
            approxSizeBytes = 1_930_000_000L,
            sizeLabel = "~1.9 GB",
            description = "Strongest on-device coding help, for higher-end phones (6 GB+ RAM). Handles longer snippets and multi-file reasoning better; slower and a larger download.",
            gated = false
        ),
        // ---- Gemma MediaPipe .task builds (gated; require a Hugging Face token) ----
        CatalogModel(
            id = "gemma3-1b-it-q4",
            name = "Gemma 3 1B · Instruct (q4) · MediaPipe",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.task",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.task?download=true",
            approxSizeBytes = 560_000_000L,
            sizeLabel = "~550 MB",
            description = "Runs on Google's MediaPipe engine. Works on most modern phones but has a small context window; prefer Qwen 2.5 1.5B if your phone can handle it.",
            gated = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        ),
        CatalogModel(
            id = "gemma3-270m-it-q8",
            name = "Gemma 3 270M · Instruct (q8) · Test only",
            fileName = "gemma3-270m-it-q8.task",
            url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task?download=true",
            approxSizeBytes = 300_000_000L,
            sizeLabel = "~300 MB",
            description = "Loads on almost any device, but too small to reliably drive the agent — good only for a quick smoke test.",
            gated = true,
            licenseUrl = "https://huggingface.co/litert-community/gemma-3-270m-it"
        )
        // Gemma 3 4B is intentionally not listed: the litert-community repo publishes it only as
        // a web (.task) build, which the on-device MediaPipe engine can't open.
    )

    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }
}
