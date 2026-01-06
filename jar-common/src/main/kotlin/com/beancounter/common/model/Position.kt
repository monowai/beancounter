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
class Position(
    val asset: Asset,
    portfolio: Portfolio?,
    tradeCurrency: Currency = Positions.getTradeCurrency(asset)
) {
    constructor(asset: Asset) : this(
        asset,
        null
    )

    @JsonIgnore
    val periodicCashFlows = PeriodicCashFlows()
    var quantityValues: QuantityValues = QuantityValues()
    var dateValues = DateValues()
    val moneyValues: MutableMap<In, MoneyValues> = EnumMap(In::class.java)

    /**
     * View currencies that cost is tracked in.
     */
    enum class In {
        TRADE,
        PORTFOLIO,
        BASE
    }

    init {
        getMoneyValues(
            In.TRADE,
            tradeCurrency
        )
        if (portfolio != null) {
            getMoneyValues(
                In.PORTFOLIO,
                portfolio.currency
            )
            getMoneyValues(
                In.BASE,
                portfolio.base
            )
        }
    }

    /**
     * MoneyValues are tracked in various currencies.
     *
     * @param reportCurrency hint as to which bucket currency represents
     * @param currency the actual currency for the reportCurrency
     * @return init reportCurrency "moneyValues" as tracking valueCurrency.
     */
    @JsonIgnore
    fun getMoneyValues(
        reportCurrency: In,
        currency: Currency
    ): MoneyValues = moneyValues.getOrPut(reportCurrency) { MoneyValues(currency) }

    @JsonIgnore
    fun getMoneyValues(reportCurrency: In): MoneyValues {
        if (moneyValues.containsKey(reportCurrency)) {
            return moneyValues[reportCurrency]!!
        }
        throw BusinessException("$reportCurrency Position does not exist for ${asset.name}")
    }

    override fun toString(): String = "Position(asset=$asset)"
}