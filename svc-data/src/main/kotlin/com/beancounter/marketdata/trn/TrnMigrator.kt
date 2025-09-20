package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Trn
import com.beancounter.common.model.Trn.Companion.VERSION_BASIC_CASH
import com.beancounter.common.model.Trn.Companion.VERSION_CASH_COST_TRACKING
import com.beancounter.common.model.Trn.Companion.VERSION_FX_RATES
import com.beancounter.marketdata.fx.FxRateService
import org.springframework.stereotype.Service

/**
 * Support class to facilitate upgrades between versions of Trn objects.
 *
 * Version History:
 * - v1: Initial transaction model
 * - v2: Basic transaction with cash currency support
 * - v3: Enhanced with FX rate calculation
 * - v4: Cash cost tracking enabled for FX/trade transactions (FX_BUY, BUY, SELL, DIVI)
 */
@Service
class TrnMigrator(
    private val fxRateService: FxRateService
) {
    fun upgrade(trn: Trn): Trn {
        if (trn.version == VERSION_BASIC_CASH) {
            return upgradeV4(upgradeV3(trn))
        }
        if (trn.version == VERSION_FX_RATES) {
            return upgradeV4(trn)
        }
        return trn
    }

    /**
     * Upgrade transaction from v2 to v3.
     * v3 adds FX rate calculation for all transactions.
     */
    private fun upgradeV3(trn: Trn): Trn {
        // Migrate from cashCurrency to cashAsset and calculate FX rates
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
        trn.version = VERSION_FX_RATES // v3: FX rates calculated
        return trn
    }

    /**
     * Upgrade transaction from v3 to v4.
     * v4 enables cash cost tracking for FX and trade transactions.
     */
    private fun upgradeV4(trn: Trn): Trn {
        trn.version = VERSION_CASH_COST_TRACKING // v4: Cash cost tracking enabled
        return trn
    }
}