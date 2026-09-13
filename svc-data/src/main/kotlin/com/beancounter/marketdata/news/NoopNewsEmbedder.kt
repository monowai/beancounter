package com.beancounter.marketdata.news

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Default [NewsEmbedder] when `beancounter.market.news.embedding.enabled` is not `true` (the
 * default). Keeps [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s embedder dependency
 * non-nullable while performing no work — `active=false` short-circuits both the dedup-clustering
 * and derived-topic ingest code paths, so this bean never touches the repo or the network.
 *
 * Mutually exclusive with [HttpNewsEmbedder]'s bean (wired by [NewsEmbeddingConfig]) via matching
 * `havingValue` conditions on the same property, rather than `@ConditionalOnMissingBean` — that
 * keeps activation independent of component-scan ordering between the two classes.
 */
@Component
@ConditionalOnProperty(
    prefix = "beancounter.market.news.embedding",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class NoopNewsEmbedder : NewsEmbedder {
    override val modelId: String = "noop"
    override val active: Boolean = false

    override fun embed(texts: List<String>): List<FloatArray> = emptyList()
}