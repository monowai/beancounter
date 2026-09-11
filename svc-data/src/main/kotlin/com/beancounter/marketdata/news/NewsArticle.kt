package com.beancounter.marketdata.news

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A news article cached from EODHD's `/api/news` endpoint.
 *
 * Articles fan out to many tickers via [NewsArticleTicker]; the EODHD response always carries the
 * full `symbols[]` list per article, so a single piece of news referencing five companies is stored
 * once and joined five ways. Tags are a simple string set ([tags]) — sufficient for the topic
 * filter without the overhead of a separate entity.
 *
 * Dedup key is [externalId] (the EODHD article link, which is stable per article). Retention is
 * driven by [published]; rows older than `eodhd.news.retention-days` (default 30) are pruned daily
 * by [NewsRetentionSchedule].
 */
@Entity
@Table(
    name = "news_article",
    uniqueConstraints = [UniqueConstraint(name = "uk_news_article_external", columnNames = ["external_id"])]
)
data class NewsArticle(
    @Column(name = "external_id", nullable = false, length = 2048)
    var externalId: String = "",
    // No default: the one call site that omits it (EodhdNewsService.saveOrMerge, for
    // a brand-new row) immediately overwrites both this and fetchedAt via
    // applyIncoming() before the entity is ever saved.
    @Column(nullable = false)
    var published: LocalDateTime,
    @Column(nullable = false, length = 1024)
    var title: String = "",
    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = "",
    @Column(length = 1024)
    var summary: String? = null,
    @Column(nullable = false, length = 2048)
    var link: String = "",
    @Column(precision = 7, scale = 4, nullable = false)
    var polarity: BigDecimal = BigDecimal.ZERO,
    @Column(name = "sentiment_pos", precision = 7, scale = 4, nullable = false)
    var sentimentPos: BigDecimal = BigDecimal.ZERO,
    @Column(name = "sentiment_neg", precision = 7, scale = 4, nullable = false)
    var sentimentNeg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "sentiment_neu", precision = 7, scale = 4, nullable = false)
    var sentimentNeu: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 16)
    var source: String = "EODHD",
    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: LocalDateTime,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "news_article_tag",
        joinColumns = [JoinColumn(name = "article_id")]
    )
    @Column(name = "tag", length = 255)
    var tags: MutableSet<String> = mutableSetOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "news_article_ticker",
        joinColumns = [JoinColumn(name = "article_id")]
    )
    var tickerLinks: MutableSet<NewsArticleTicker> = mutableSetOf(),
    /**
     * Little-endian float32 embedding vector (see [EmbeddingCodec]), computed by [NewsEmbedder]
     * at ingest. Null until the feature is enabled and this row has been (re)embedded — see
     * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s ingest seam.
     *
     * No `@Lob`: a plain `byte[]` column maps to `VARBINARY`, which `PostgreSQLDialect` resolves
     * to `bytea` (matching the V35 migration). `@Lob` would push Hibernate onto the LOB streaming
     * API, which on some Postgres/driver combinations resolves to `oid` instead — the wrong type
     * for this column.
     */
    @Column(name = "embedding")
    var embedding: ByteArray? = null,
    /**
     * Model identifier the [embedding] vector was computed with (e.g. `all-minilm-l6-v2`).
     * Compared against [NewsEmbedder.modelId] at ingest to decide whether a row needs
     * re-embedding — a model change invalidates every previously stored vector.
     */
    @Column(name = "embedding_model", length = 64)
    var embeddingModel: String? = null,
    /**
     * Topics derived from anchor-phrase cosine similarity ([TopicAnchors]), distinct from the
     * vendor's sparse EODHD [tags]. Backs the same topic filter as `tags` (see
     * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s `matchesTopic`), closing the
     * coverage gap when EODHD ships no tags at all.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "news_article_topic",
        joinColumns = [JoinColumn(name = "article_id")]
    )
    @Column(name = "topic", length = 64)
    var derivedTopics: MutableSet<String> = mutableSetOf()
) {
    @Id
    val id: String = KeyGenUtils().id
}