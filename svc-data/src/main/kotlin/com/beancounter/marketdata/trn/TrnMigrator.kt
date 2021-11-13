package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.fx.FxRateService
import org.springframework.stereotype.Service

/**
 * Support class to facilitate upgrades between versions of Trn objects.
 */
@Service
class TrnMigrator(private val cashServices: CashServices, private val fxRateService: FxRateService) {
    fun upgrade(trn: Trn): Trn {
        if (trn.version == "1") {
            return upgradeV2(trn)
        }
        return trn
    }

    @Suppress("DEPRECATION")
    private fun upgradeV2(trn: Trn): Trn {
        // Migrate from cashCurrency to cashAsset
        if (TrnType.isCashImpacted(trn.trnType) && trn.cashCurrency == null) {
            // Upgrade the cash currency which was not set.
            // cash currency is secondary to
            trn.cashCurrency = trn.asset.market.currency
        }
        val pair = IsoCurrencyPair(trn.tradeCurrency.code, trn.cashCurrency!!.code)
        val cashRates = fxRateService.getRates(
            FxRequest(
                trn.tradeDate.toString(),
                arrayListOf(pair)
            )
        )

        val trnInput = TrnInput(
            assetId = trn.asset.id,
            price = trn.price!!,
            callerRef = trn.callerRef!!,
            cashAssetId = if (trn.cashAsset == null) null else trn.cashAsset!!.id,
            cashCurrency = if (trn.cashCurrency == null) null else trn.cashCurrency!!.code,
            tradeDate = trn.tradeDate,
            trnType = trn.trnType,
            tradeAmount = trn.tradeAmount,
            cashAmount = trn.cashAmount,
            tradeBaseRate = trn.tradeBaseRate,
            tradePortfolioRate = trn.tradePortfolioRate,
            tradeCashRate = cashRates.data.rates[pair]!!.rate,
            quantity = trn.quantity,
        )
        val cashAmount = cashServices.getCashImpact(trnInput)
        val cashAsset = cashServices.getCashAsset(trnInput)
        trn.version = "2" // Cash is upgraded to this point.
        trn.cashAmount = cashAmount
        trn.cashAsset = cashAsset
        trn.cashCurrency = null
        trn.tradeCashRate = cashRates.data.rates[pair]!!.rate
        // store
        return trn
    }
}
