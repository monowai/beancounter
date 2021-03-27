package com.beancounter.common.contracts

import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.ConstructorBinding
import java.util.ArrayList

data class FxRequest @ConstructorBinding constructor(
    val rateDate: String = DateUtils.today,
    val pairs: ArrayList<IsoCurrencyPair> = ArrayList()
) {
    constructor() : this(DateUtils.today, ArrayList())
    constructor(rateDate: String) : this(rateDate, ArrayList())

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
