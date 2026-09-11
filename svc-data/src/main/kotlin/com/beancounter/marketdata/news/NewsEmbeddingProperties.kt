package com.beancounter.marketdata.news

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Tunables for news embeddings (dedup clustering + derived-topic tagging). The embedding model
 * itself runs out-of-process — a standalone HuggingFace Text Embeddings Inference deployment
 * (`bc-embed`) — so svc-data only needs an HTTP client's config and the similarity thresholds
 * here; see [HttpNewsEmbedder].
 *
 * DEFAULT-OFF: `enabled=false` keeps [NoopNewsEmbedder] active and the whole pipeline dormant
 * until `bc-embed` is deployed and [url] is configured.
 */
@ConfigurationProperties(prefix = "beancounter.market.news.embedding")
data class NewsEmbeddingProperties(
    /** Master switch. Off by default — see class KDoc. */
    val enabled: Boolean = false,
    /** Base URL of the bc-embed (TEI) deployment, e.g. `http://bc-embed`. Empty until deployed. */
    val url: String = "",
    /** Stamped on [NewsArticle.embeddingModel] — a model change invalidates every stored vector. */
    val modelId: String = "all-minilm-l6-v2",
    /** Cosine similarity at/above which two articles are considered near-duplicates at rank time. */
    val similarityThreshold: Double = 0.90,
    /** Cosine similarity at/above which an article is tagged with a topic anchor at ingest. */
    val topicThreshold: Double = 0.55,
    /** Articles per `/embed` request to bc-embed. */
    val batchSize: Int = 32
)