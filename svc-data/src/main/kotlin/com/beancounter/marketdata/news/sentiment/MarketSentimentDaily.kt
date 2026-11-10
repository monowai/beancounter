package com.beancounter.marketdata.news.sentiment

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One day of Positive / Neutral / Negative article counts for one market scope (V49).
 *
 * Natural key is `(scopeKind, scopeName, newsDate)` with a surrogate [id], the same shape as
 * [NewsSentimentDaily]. [MarketSentimentService.rollup] recounts a trailing window on every run
 * and updates the row in place. Rows are never pruned: they are the only record of sentiment once
 * the articles behind them age out.
 */
@Entity
@Table(
    name = "news_market_sentiment_daily",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_news_market_sentiment_daily",
            columnNames = ["scope_kind", "scope_name", "news_date"]
        )
    ]
)
data class MarketSentimentDaily(
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_kind", nullable = false, length = 16)
    var scopeKind: SentimentScopeKind = SentimentScopeKind.TOPIC,
    @Column(name = "scope_name", nullable = false, length = 64)
    var scopeName: String = "",
    // MIN is a JPA no-arg-constructor placeholder, never read — callers always pass the day counted.
    @Column(name = "news_date", nullable = false)
    var newsDate: LocalDate = LocalDate.MIN,
    @Column(nullable = false)
    var positive: Int = 0,
    @Column(nullable = false)
    var neutral: Int = 0,
    @Column(nullable = false)
    var negative: Int = 0,
    @Column(nullable = false, precision = 6, scale = 4)
    var polarity: BigDecimal = BigDecimal.ZERO,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.MIN
) {
    @Id
    val id: String = KeyGenUtils().id
}