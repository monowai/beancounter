package com.beancounter.marketdata.news

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One day of EODHD aggregated news sentiment for one asset, from `GET /api/sentiments`.
 *
 * Natural key is `(assetId, priceDate)`, enforced by the unique constraint (V39) with a surrogate
 * [id] — the same shape as `market_data`. [NewsSentimentService.refresh] re-fetches a trailing
 * window on every run and updates the existing row in place, so late-arriving article counts
 * overwrite rather than duplicate a day.
 *
 * [symbol] is the fully-qualified EODHD ticker (`CODE.EXCHANGE`) the row was fetched under —
 * kept for debugging symbol routing, not for lookups.
 */
@Entity
@Table(
    name = "news_sentiment_daily",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_news_sentiment_daily", columnNames = ["asset_id", "price_date"])
    ]
)
data class NewsSentimentDaily(
    @Column(name = "asset_id", nullable = false, length = 36)
    var assetId: String = "",
    // MIN is a JPA no-arg-constructor placeholder, never read — callers always pass the EODHD
    // date (ZoneLeakGuardTest forbids default-zone now() here).
    @Column(name = "price_date", nullable = false)
    var priceDate: LocalDate = LocalDate.MIN,
    @Column(nullable = false, length = 32)
    var symbol: String = "",
    @Column(name = "article_count", nullable = false)
    var articleCount: Int = 0,
    @Column(nullable = false, precision = 6, scale = 4)
    var normalized: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: LocalDateTime = LocalDateTime.MIN
) {
    @Id
    val id: String = KeyGenUtils().id
}