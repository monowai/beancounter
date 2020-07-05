package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.*

/**
 * Represents an Asset held in a Portfolio.
 *
 * @author mikeh
 * @since 2019-01-28
 */
data class Position(val asset: Asset) {

    var quantityValues: QuantityValues = QuantityValues()
    var dateValues = DateValues()
    val moneyValues: MutableMap<In, MoneyValues> = EnumMap(In::class.java)

    enum class In {
        TRADE, PORTFOLIO, BASE
    }

    @JsonIgnore
    fun getMoneyValues(reportCurrency: In): MoneyValues? {
        return moneyValues[reportCurrency]
    }

    /**
     * MoneyValues are tracked in various currencies.
     *
     * @param reportCurrency which model
     * @return init reportCurrency "moneyValues" as tracking valueCurrency.
     */
    @JsonIgnore
    fun getMoneyValues(reportCurrency: In, currency: Currency): MoneyValues {
        var result = moneyValues[reportCurrency]
        if (result == null) {
            result = MoneyValues(currency)
            moneyValues[reportCurrency] = result
        }
        return result
    }

}