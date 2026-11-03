package com.beancounter.position.valuation

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils

/**
 * One request to value several portfolios as a single set of positions.
 *
 * @param portfolios the portfolios to aggregate; the first is the context portfolio
 * @param valuationDate date for position valuation
 * @param value whether to include market values
 * @param targetCurrencyCode optional ISO currency code (e.g. "SGD") to use as the
 *   PORTFOLIO reporting currency. When supplied, the [contextPortfolio] adopts it so
 *   MarketValue prices each bucket directly against the user's requested currency,
 *   avoiding the FX round-trip drift that surfaces when the first portfolio's currency
 *   differs from the caller's target. PRIVATE / POLICY positions are most sensitive
 *   (constant `close=1`) because the round-trip rate product isn't exactly 1.0.
 * @param live value at live (delayed) quotes while each market is in session. Display
 *   only: an aggregate never publishes market value.
 */
data class AggregateValuationRequest(
    val portfolios: Collection<Portfolio>,
    val valuationDate: String = DateUtils.TODAY,
    val value: Boolean,
    val targetCurrencyCode: String? = null,
    val live: Boolean = false
) {
    /** The requested reporting currency, upper-cased; blank stays blank. */
    val targetCurrency: String? get() = targetCurrencyCode?.uppercase()

    /**
     * The portfolio the aggregate reports as: the first one, owner and ids included,
     * in the target currency when one is requested and differs from its own.
     */
    fun contextPortfolio(): Portfolio {
        val first = portfolios.first()
        return if (!targetCurrencyCode.isNullOrBlank() &&
            !first.currency.code.equals(targetCurrencyCode, ignoreCase = true)
        ) {
            first.copy(currency = Currency(targetCurrencyCode.uppercase()))
        } else {
            first
        }
    }
}