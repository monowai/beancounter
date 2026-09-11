package com.beancounter.marketdata.news

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Wires [HttpNewsEmbedder] as the [NewsEmbedder] bean only when
 * `beancounter.market.news.embedding.enabled=true`. The flag-off default — and every current
 * `@SpringBootTest` context, none of which sets this property — never constructs this bean, so
 * [NoopNewsEmbedder] stays the sole [NewsEmbedder] everywhere that doesn't explicitly opt in.
 *
 * [NewsEmbeddingProperties] itself is registered unconditionally (see
 * `com.beancounter.marketdata.providers.eodhd.EodhdConfig`) because
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService] needs its thresholds regardless of
 * whether embedding is currently active — rank-time dedup clustering runs off already-persisted
 * vectors even after the feature is switched back off.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "beancounter.market.news.embedding",
    name = ["enabled"],
    havingValue = "true"
)
class NewsEmbeddingConfig {
    @Bean
    fun httpNewsEmbedder(
        @Qualifier("newsEmbeddingRestClient") restClient: RestClient,
        properties: NewsEmbeddingProperties
    ): NewsEmbedder = HttpNewsEmbedder(restClient, properties)
}