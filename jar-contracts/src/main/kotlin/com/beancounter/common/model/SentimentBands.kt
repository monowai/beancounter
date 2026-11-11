package com.beancounter.common.model

import java.math.BigDecimal

/**
 * The polarity cut-offs that split news sentiment into Positive, Neutral and Negative. One
 * definition, shared by svc-data's per-article label and daily sentiment counts and by the tone
 * svc-agent words for a holding, so a headline labelled Bullish is always counted as positive.
 */
object SentimentBands {
    /** Polarity at or above this is positive. */
    val POSITIVE: BigDecimal = BigDecimal("0.35")

    /** Polarity at or below this is negative. */
    val NEGATIVE: BigDecimal = BigDecimal("-0.35")
}