package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef.Companion.from
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.key.KeyGenUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service

/**
 * Map TrnInput objects to the Trn persistent model.
 */
@Service
class TrnAdapter internal constructor(
    var assetService: AssetService,
    var currencyService: CurrencyService,
    var tradeCalculator: TradeCalculator,
    val cashServices: CashServices,
    private val keyGenUtils: KeyGenUtils = KeyGenUtils()
) {
    fun convert(portfolio: Portfolio, trnRequest: TrnRequest): TrnResponse {
        val trns = ArrayList<Trn>()
        for (trnInput in trnRequest.data) {
            trns.add(map(portfolio, trnInput))
        }
        return TrnResponse(trns)
    }

    fun map(portfolio: Portfolio, trnInput: TrnInput): Trn {
        val cashAsset = cashServices.getCashAsset(trnInput)
        return Trn(
            id = keyGenUtils.id,
            callerRef = from(trnInput.callerRef, portfolio),
            trnInput.trnType,
            trnInput.status,
            portfolio = portfolio,
            asset = assetService.find(trnInput.assetId),
            tradeCurrency = currencyService.getCode(trnInput.tradeCurrency)!!,
            cashCurrency = null,
            cashAsset = cashAsset,
            tradeDate = trnInput.tradeDate,
            settleDate = trnInput.settleDate,
            quantity = trnInput.quantity,
            price = trnInput.price,
            fees = trnInput.fees,
            tax = trnInput.tax,
            tradeAmount = tradeCalculator.amount(trnInput),
            cashAmount = cashServices.getCashImpact(trnInput),
            tradeCashRate = trnInput.tradeCashRate,
            tradeBaseRate = trnInput.tradeBaseRate,
            tradePortfolioRate = trnInput.tradePortfolioRate,
            comments = trnInput.comments
        )
    }

    fun hydrate(asset: Asset?): Asset? {
        return if (asset == null) {
            null
        } else assetService.find(asset.id)
    }
}
