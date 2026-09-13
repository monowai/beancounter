package com.beancounter.marketdata.news

/**
 * Turns article text into dense vectors for near-duplicate clustering and derived-topic tagging.
 *
 * Deliberately its own interface rather than a direct dependency on a vendor SDK type: (1) it
 * isolates the embedding backend — an HTTP call to a standalone `bc-embed` deployment, see
 * [HttpNewsEmbedder] — to a single implementation, so the rest of the news pipeline never imports
 * it, and (2) it lets [NoopNewsEmbedder] satisfy [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s
 * constructor as a required (non-nullable) dependency when the feature is off, instead of
 * threading a nullable embedder through the service and every call site.
 */
interface NewsEmbedder {
    /** Model identifier stamped on [NewsArticle.embeddingModel] so a model change invalidates the cache. */
    val modelId: String

    /** False when embeddings are disabled (or unavailable) — callers must skip the whole pipeline. */
    val active: Boolean

    /** Embeds [texts] in the given order. Implementations decide batching internally. */
    fun embed(texts: List<String>): List<FloatArray>
}