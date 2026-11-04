package com.beancounter.marketdata.news.eodhd

import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The keys EODHD news is stored and fetched under. A key is either an exchange-qualified symbol
 * (`AAPL.US`, `GSPC.INDX`) or a synthetic topic key (`TOPIC:STOCK_MARKETS`); both land in the
 * same `news_fetch.ticker` / `news_article_ticker.ticker` columns, so one refresh / rank pipeline
 * serves ticker, market and topic news.
 */
@Component
class EodhdNewsSymbols(
    private val eodhdConfig: EodhdConfig,
    private val assetFinder: AssetFinder
) {
    private val log = LoggerFactory.getLogger(EodhdNewsSymbols::class.java)

    /** BC tickers plus an optional BC market code -> EODHD symbols (`BRK.B` in US -> `BRK-B.US`). */
    fun forTickers(
        tickers: String,
        market: String?
    ): List<String> {
        val exchange =
            if (market.isNullOrBlank()) {
                "US"
            } else {
                // Caller-supplied market codes are not guaranteed to be ours, and
                // MarketService is @Transactional — catching its exception left the
                // caller's transaction rollback-only (#1088).
                eodhdConfig.marketService
                    .getMarketOrNull(market)
                    ?.getAlias(EodhdPriceService.ID)
                    ?: market.also { log.debug("No EODHD alias for market {}", it) }
            }
        val bcMarket = if (market.isNullOrBlank()) "US" else market
        return tickers
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.matches(TICKER_PATTERN) }
            .map { "${priceSymbolFor(it, bcMarket)}.$exchange" }
    }

    /** Already exchange-qualified proxy symbols (`GSPC.INDX`), normalised but never re-resolved. */
    fun forMarket(symbols: List<String>): List<String> =
        symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }

    /**
     * Topic key -> original (untruncated) topic, so the refresh can recover the full EODHD tag
     * even when the key itself was truncated to fit the ticker columns (see [topicTagFor]). Built
     * via associateBy so a key collision after truncation just keeps the last topic that produced
     * it, rather than fetching for both.
     */
    fun forTopics(topics: List<String>): Map<String, String> =
        topics
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .associateBy { topicKey(it) }

    /**
     * Map a BC asset code to the symbol EODHD actually indexes news under. BC stores the canonical
     * EODHD ticker in `Asset.priceSymbol` (e.g. code `BRK.B` → priceSymbol `BRK-B`, since EODHD uses
     * a hyphen for US class shares). Without this, a naive `code.exchange` build (`BRK.B.US`) has no
     * EODHD coverage and the agent wrongly falls back to general knowledge. Unknown tickers (not held
     * locally) and lookup failures fall back to the raw code so ad-hoc queries still work.
     */
    private fun priceSymbolFor(
        code: String,
        bcMarket: String
    ): String =
        // findLocally now returns null for an unknown market instead of throwing, so the
        // "not held locally" fallback no longer needs a catch that would have left the
        // caller's transaction rollback-only (#1088).
        assetFinder
            .findLocally(AssetInput(bcMarket, code))
            ?.priceSymbol
            ?.takeIf { it.isNotBlank() }
            ?: code.also { log.debug("No local asset for {}/{}", bcMarket, it) }

    companion object {
        private val TICKER_PATTERN = Regex("[A-Z0-9.-]{1,10}")

        // Synthetic key prefix distinguishing a topic-tag query from a ticker/index symbol in the
        // shared refresh/rank pipeline. See [topicKey] / [topicTagFor].
        private const val TOPIC_PREFIX = "TOPIC:"

        // news_fetch.ticker / news_article_ticker.ticker are VARCHAR(32). TOPIC_PREFIX is 6 chars,
        // so the normalized topic suffix gets 32 - 6 = 26.
        private const val TOPIC_KEY_MAX_SUFFIX = 26

        fun isTopicKey(key: String): Boolean = key.startsWith(TOPIC_PREFIX)

        /**
         * "stock markets" -> "TOPIC:STOCK_MARKETS". Truncated to [TOPIC_KEY_MAX_SUFFIX] chars so the
         * result always fits the `news_fetch.ticker` / `news_article_ticker.ticker` VARCHAR(32) columns
         * regardless of how long an arbitrary `/news/topic` input is. Truncation is lossy — see
         * [topicTagFor] for why the upstream EODHD call must NOT be derived from this key.
         */
        fun topicKey(topic: String): String =
            TOPIC_PREFIX +
                topic
                    .trim()
                    .uppercase()
                    .replace(" ", "_")
                    .take(TOPIC_KEY_MAX_SUFFIX)

        /**
         * The EODHD tag for topic key [key]. Prefers the original, untruncated topic carried in
         * [topicTags] (populated by [forTopics] for every key it mints) — [topicKey] truncates to
         * fit the VARCHAR(32) ticker columns, so reconstructing the tag FROM the (possibly truncated)
         * key would silently send EODHD a corrupted tag for any topic longer than
         * [TOPIC_KEY_MAX_SUFFIX] characters. The key-derived fallback only exists for callers that
         * don't have (or need) the original — it's exact for any key that was never truncated.
         */
        fun topicTagFor(
            key: String,
            topicTags: Map<String, String>
        ): String = topicTags[key]?.lowercase() ?: key.removePrefix(TOPIC_PREFIX).lowercase().replace("_", " ")
    }
}