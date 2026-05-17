package com.beancounter.marketdata.providers.eodhd.news

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
    @Column(nullable = false)
    var published: LocalDateTime = LocalDateTime.now(),
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
    var fetchedAt: LocalDateTime = LocalDateTime.now(),
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
    var tickerLinks: MutableSet<NewsArticleTicker> = mutableSetOf()
) {
    @Id
    val id: String = KeyGenUtils().id
}