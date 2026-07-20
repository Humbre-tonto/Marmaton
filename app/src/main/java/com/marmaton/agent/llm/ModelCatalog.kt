package com.marmaton.agent.llm

/**
 * A single downloadable on-device model. The catalog targets MediaPipe LLM Inference
 * `.task` bundles (the format the app's [LocalFileBackend] already loads), hosted by the
 * Google AI Edge / LiteRT Community on Hugging Face.
 *
 * Most Gemma weights are license-gated on Hugging Face, so [gated] models require the user to
 * accept the model's license once on the model page and supply a Hugging Face access token
 * (stored via [SecurePreferences.getHuggingFaceToken]). The token is sent only as a Bearer
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
        CatalogModel(
            id = "gemma3-270m-it-q8",
            name = "Gemma 3 270M · Instruct (q8)",
            fileName = "gemma3-270m-it-q8.task",
            url = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task?download=true",
            approxSizeBytes = 300_000_000L,
            sizeLabel = "~300 MB",
            description = "Tiny and fast — runs on almost any device. Best for trying the agent.",
            gated = true,
            licenseUrl = "https://huggingface.co/litert-community/gemma-3-270m-it"
        ),
        CatalogModel(
            id = "gemma3-1b-it-q4",
            name = "Gemma 3 1B · Instruct (q4)",
            fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.task",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.task?download=true",
            approxSizeBytes = 560_000_000L,
            sizeLabel = "~550 MB",
            description = "Balanced quality and size for mid-range phones.",
            gated = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        ),
        CatalogModel(
            id = "gemma3-4b-it-q4",
            name = "Gemma 3 4B · Instruct (q4)",
            fileName = "Gemma3-4B-IT_multi-prefill-seq_q4_ekv4096.task",
            url = "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/Gemma3-4B-IT_multi-prefill-seq_q4_ekv4096.task?download=true",
            approxSizeBytes = 3_100_000_000L,
            sizeLabel = "~3.1 GB",
            description = "Highest quality — needs a recent high-end device with plenty of RAM and storage.",
            gated = true,
            licenseUrl = "https://huggingface.co/litert-community/Gemma3-4B-IT"
        )
    )

    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }
}
