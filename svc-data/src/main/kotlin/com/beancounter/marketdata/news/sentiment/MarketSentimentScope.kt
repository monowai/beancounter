package com.beancounter.marketdata.news.sentiment

import com.beancounter.marketdata.macro.MacroRefreshSchedule
import com.beancounter.marketdata.news.eodhd.EodhdNewsSymbols

/** What a market sentiment series is about. Series of different kinds are never combined. */
enum class SentimentScopeKind {
    TOPIC,
    SECTOR
}

/**
 * One market sentiment series: a macro topic or a sector. [key] is the key its articles are
 * stored under in `news_article_ticker.ticker`; it never leaves svc-data.
 */
data class MarketSentimentScope(
    val kind: SentimentScopeKind,
    val name: String,
    val key: String
)

/** The scopes whose news is downloaded on a schedule and rolled up into daily sentiment. */
object MarketSentimentScopes {
    val TOPICS: List<String> = MacroRefreshSchedule.MACRO_TOPICS

    // Sector news is read through the SPDR sector ETFs. Must match NewsTools.SECTOR_ETFS in
    // svc-agent, which has no compile-time dependency on svc-data.
    val SECTORS: Map<String, String> =
        mapOf(
            "technology" to "XLK.US",
            "financials" to "XLF.US",
            "energy" to "XLE.US",
            "healthcare" to "XLV.US",
            "consumer_discretionary" to "XLY.US",
            "consumer_staples" to "XLP.US",
            "industrials" to "XLI.US",
            "materials" to "XLB.US",
            "utilities" to "XLU.US",
            "real_estate" to "XLRE.US",
            "communication" to "XLC.US"
        )

    val ALL: List<MarketSentimentScope> =
        TOPICS.map { MarketSentimentScope(SentimentScopeKind.TOPIC, it, EodhdNewsSymbols.topicKey(it)) } +
            SECTORS.map { (name, symbol) -> MarketSentimentScope(SentimentScopeKind.SECTOR, name, symbol) }
}