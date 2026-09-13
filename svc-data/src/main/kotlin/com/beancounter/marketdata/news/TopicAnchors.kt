package com.beancounter.marketdata.news

/**
 * BC's derived-topic vocabulary, each mapped to an anchor phrase whose embedding stands in for
 * the topic. An article's derived topics are the anchors whose cosine similarity to the article's
 * own embedding clears `beancounter.market.news.embedding.topic-threshold` — see
 * [TopicAnchorStore.matchingTopics]. Deliberately small and hand-curated rather than derived from
 * EODHD's tag vocabulary: EODHD tags are sparse (many articles carry none), which is exactly the
 * coverage gap this closes.
 */
object TopicAnchors {
    val PHRASES: Map<String, String> =
        mapOf(
            "stock markets" to "broad stock market moves, index selloffs and rallies",
            "economy" to "macroeconomic conditions, growth and recession",
            "inflation" to "consumer prices and inflation data",
            "bonds" to "treasury yields and bond markets",
            "energy" to "oil and gas prices and energy markets",
            "commodities" to "commodity prices, metals and raw materials"
        )
}