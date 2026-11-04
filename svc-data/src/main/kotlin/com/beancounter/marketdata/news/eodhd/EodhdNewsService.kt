package com.beancounter.marketdata.news.eodhd

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsFetch
import com.beancounter.marketdata.news.NewsProvider
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * DB-backed EODHD news adapter. The read path always returns from the [NewsArticle] table; the
 * write path only re-hits EODHD when the per-ticker entry in [NewsFetch] is older than
 * `eodhd.news.refresh-after-hours` (default 6h). Articles persist for `retention-days` (default 30)
 * and the daily [com.beancounter.marketdata.news.NewsRetentionSchedule] prunes
 * anything older.
 *
 * The projection back to the caller keeps the AV-compatible `{feed, count}` shape so
 * svc-agent's NewsTools doesn't change. EODHD-specific fields (`polarity`, `tags`, `symbols`) are
 * also returned because they're useful context for the LLM.
 */
@Service
class EodhdNewsService(
    private val newsProperties: EodhdNewsProperties,
    private val newsArticleRepo: NewsArticleRepo,
    private val symbols: EodhdNewsSymbols,
    private val refresh: EodhdNewsRefresh,
    embeddingProperties: NewsEmbeddingProperties,
    private val dateUtils: DateUtils = DateUtils()
) : NewsProvider {
    private val briefing = NewsBriefing(newsProperties, embeddingProperties)

    @Transactional
    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> = newsForSymbols(symbols.forTickers(tickers, market), topics)

    /**
     * Market / sector news via proxy symbols. svc-agent passes already-resolved EODHD symbols
     * (e.g. `GSPC.INDX` for the S&P 500, `XLK.US` for the tech sector SPDR) so macro and sector-wide
     * moves surface even when no held ticker is tagged in the article. Symbols are verbatim — unlike
     * [getNewsSentiment] there's no ticker+market resolution step — and otherwise share the same
     * DB-backed refresh / rank / project pipeline.
     */
    @Transactional
    override fun getMarketNews(
        symbols: List<String>,
        topics: String?
    ): Map<String, Any> = newsForSymbols(this.symbols.forMarket(symbols), topics)

    /**
     * Broad macro/topic news (`stock markets`, `economy`, `inflation`, …) rather than news pinned
     * to a ticker or index. Each topic maps to a synthetic key (`TOPIC:STOCK_MARKETS`) that reuses
     * the exact same DB-backed refresh / cooldown / rank / project pipeline as ticker news — the
     * key is stored as the article's "ticker" tag by [EodhdNewsIngest], so
     * [NewsArticleRepo.findByTickersAfter] finds it the same way it finds a held symbol.
     */
    @Transactional
    override fun getTopicNews(topics: List<String>): Map<String, Any> {
        val keyToTopic = symbols.forTopics(topics)
        return newsForSymbols(keyToTopic.keys.toList(), null, keyToTopic)
    }

    /**
     * Shared read path: refresh any stale symbols from upstream, then rank the stored articles and
     * project to the `{feed, count}` shape. Returns an empty map when no symbols resolve or nothing
     * ranks — the no-coverage signal svc-agent's NewsTools expects.
     */
    private fun newsForSymbols(
        symbols: List<String>,
        topics: String?,
        topicTags: Map<String, String> = emptyMap()
    ): Map<String, Any> {
        if (symbols.isEmpty()) return emptyMap()

        refresh.refreshStale(symbols, topicTags)

        val retentionStart = LocalDateTime.now(dateUtils.zoneId).minusDays(newsProperties.retentionDays)
        val stored = newsArticleRepo.findByTickersAfter(symbols, retentionStart)
        val ranked = briefing.rank(stored, symbols, topics).map { briefing.project(it) }

        if (ranked.isEmpty()) return emptyMap()
        return mapOf(
            "feed" to ranked,
            "count" to ranked.size
        )
    }
}