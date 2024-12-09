package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Trn
import com.beancounter.marketdata.fx.FxRateService
import org.springframework.stereotype.Service

/**
 * Support class to facilitate upgrades between versions of Trn objects.
 */
@Service
class TrnMigrator(
    private val fxRateService: FxRateService
) {
    fun upgrade(trn: Trn): Trn {
        if (trn.version == "2") {
            return upgradeV3(trn)
        }
        return trn
    }

    private fun upgradeV3(trn: Trn): Trn {
        // Migrate from cashCurrency to cashAsset
        var tradeCash: IsoCurrencyPair? = null
        if (trn.cashAsset != null) {
            tradeCash =
                IsoCurrencyPair(
                    trn.tradeCurrency.code,
                    trn.cashCurrency!!.code
                )
        }
        val tradePortfolio =
            IsoCurrencyPair(
                trn.tradeCurrency.code,
                trn.portfolio.currency.code
            )
        val tradeBase =
            IsoCurrencyPair(
                trn.tradeCurrency.code,
                trn.portfolio.base.code
            )

        val rateList =
            if (tradeCash == null) {
                mutableSetOf(
                    tradePortfolio,
                    tradeBase
                )
            } else {
                mutableSetOf(
                    tradePortfolio,
                    tradeBase,
                    tradeCash
                )
            }
        val rates =
            fxRateService.getRates(
                FxRequest(
                    trn.tradeDate.toString(),
                    rateList
                ),
                "token"
            )
        trn.tradeBaseRate = rates.data.rates[tradeBase]!!.rate
        trn.tradePortfolioRate = rates.data.rates[tradePortfolio]!!.rate
        if (tradeCash != null) {
            trn.tradeCashRate = rates.data.rates[tradeCash]!!.rate
        }
        trn.version = "3" // Cash is upgraded to this point.
        // store
        return trn
    }
}