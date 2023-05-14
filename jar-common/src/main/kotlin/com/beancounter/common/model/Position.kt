package com.beancounter.common.model

import com.beancounter.common.exception.BusinessException
import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.EnumMap

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

    /**
     * View currencies that cost is tracked in.
     */
    enum class In {
        TRADE, PORTFOLIO, BASE
    }

    /**
     * MoneyValues are tracked in various currencies.
     *
     * @param reportCurrency hint as to which bucket currency represents
     * @param currency the actual currency for the reportCurrency
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

    @JsonIgnore
    fun getMoneyValues(reportCurrency: In): MoneyValues {
        if (moneyValues.containsKey(reportCurrency)) {
            return moneyValues[reportCurrency]!!
        }
        throw BusinessException("$reportCurrency Position does not exist for ${asset.name}")
    }
}
