package com.beancounter.marketdata.news.sentiment

import java.math.BigDecimal

/**
 * The polarity cut-offs that split articles into Positive, Neutral and Negative. One definition,
 * shared by the per-article label an agent reads and the daily market sentiment counts, so a
 * headline labelled Bullish is always counted as positive.
 */
object SentimentBands {
    /** Polarity at or above this is positive. */
    val POSITIVE: BigDecimal = BigDecimal("0.35")

    /** Polarity at or below this is negative. */
    val NEGATIVE: BigDecimal = BigDecimal("-0.35")
}