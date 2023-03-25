package com.beancounter.common.contracts

import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Request to locate FX Rates
 */
data class FxRequest constructor(
    val rateDate: String = DateUtils.today,
    val pairs: ArrayList<IsoCurrencyPair> = arrayListOf(),
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
            if (!pairs.contains(isoCurrencyPair)) {
                pairs.add(isoCurrencyPair)
            }
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
