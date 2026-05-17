package com.beancounter.marketdata.providers.eodhd.news

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Row in the `news_article_ticker` join table. Stored as an `@ElementCollection` on
 * [NewsArticle.tickerLinks] rather than as a bidirectional `@Entity` so the data-class hashCode
 * doesn't cycle. The DB primary key `(article_id, ticker)` is enforced by the V19 migration.
 *
 * [ticker] is the raw EODHD symbol verbatim (e.g. `AAPL.US`, `0ZG.F`). [assetId] is populated
 * opportunistically when the ticker resolves to a BC asset row; null otherwise.
 */
@Embeddable
data class NewsArticleTicker(
    @Column(name = "ticker", length = 32, nullable = false)
    var ticker: String = "",
    @Column(name = "asset_id", length = 36)
    var assetId: String? = null
)