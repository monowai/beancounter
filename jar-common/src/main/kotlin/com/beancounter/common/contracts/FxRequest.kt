package com.beancounter.common.contracts

import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Represents a foreign exchange request for obtaining currency conversion rates for specified currency pairs on a given date.
 * This class stores a collection of unique currency pairs for which rates are requested and provides functionality to add currency pairs
 * related to various financial transactions like portfolio, cash management, and base currency operations.
 *
 * The currency pairs are stored in a way that ensures there are no duplicates, preserving the integrity of the request.
 *
 * Usage:
 * - `add(isoCurrencyPair: IsoCurrencyPair?)`: Adds a new currency pair to the request if it isn't already present.
 * - `addTradePf(tradePf: IsoCurrencyPair?)`: Adds a currency pair related to portfolio transactions and ensures it's included in the rate request.
 * - `addTradeBase(tradeBase: IsoCurrencyPair?)`: Adds a currency pair used as the base in transactions.
 * - `addTradeCash(tradeCash: IsoCurrencyPair?)`: Adds a currency pair related to cash transactions.
 *
 * @property rateDate The date for which the exchange rates are requested, defaulting to today.
 * @property pairs The set of unique currency pairs for which rates are needed.
 * @property tradePf Optional portfolio-related currency pair.
 * @property tradeCash Optional cash-related currency pair.
 * @property tradeBase Optional base currency pair.
 */
data class FxRequest(
    val rateDate: String = DateUtils.TODAY,
    val pairs: MutableSet<IsoCurrencyPair> = mutableSetOf(),
) {
    @JsonIgnore
    var tradePf: IsoCurrencyPair? = null

    @JsonIgnore
    var tradeCash: IsoCurrencyPair? = null

    @JsonIgnore
    var tradeBase: IsoCurrencyPair? = null

    @JsonIgnore
    fun add(isoCurrencyPair: IsoCurrencyPair?): FxRequest {
        if (isoCurrencyPair != null) {
            pairs.add(isoCurrencyPair)
        }
        return this
    }

    fun addTradePf(tradePf: IsoCurrencyPair?) {
        this.tradePf = tradePf
        add(tradePf)
    }

    fun addTradeBase(tradeBase: IsoCurrencyPair?) {
        this.tradeBase = tradeBase
        add(tradeBase)
    }

    fun addTradeCash(tradeCash: IsoCurrencyPair?) {
        this.tradeCash = tradeCash
        add(tradeCash)
    }
}
