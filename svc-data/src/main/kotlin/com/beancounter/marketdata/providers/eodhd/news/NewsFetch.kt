package com.beancounter.marketdata.providers.eodhd.news

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Per-ticker fetch metadata. Gates the next EODHD news request: if `lastFetchedAt` is older than
 * `eodhd.news.refresh-after-hours`, the next /news call for that ticker re-hits EODHD; otherwise we
 * serve entirely from the [NewsArticle] table.
 */
@Entity
@Table(name = "news_fetch")
data class NewsFetch(
    @Id
    @Column(nullable = false, length = 32)
    var ticker: String = "",
    @Column(name = "last_fetched_at", nullable = false)
    var lastFetchedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "articles_found", nullable = false)
    var articlesFound: Int = 0
)