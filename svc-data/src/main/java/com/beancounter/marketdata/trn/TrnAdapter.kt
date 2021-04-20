package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef.Companion.from
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service

/**
 * Adapt TrnInput objects to the Trn persistent model.
 */
@Service
class TrnAdapter internal constructor(
    var assetService: AssetService,
    var currencyService: CurrencyService,
    var tradeCalculator: TradeCalculator,
    private val keyGenUtils: KeyGenUtils
) {
    fun convert(portfolio: Portfolio, trnRequest: TrnRequest): TrnResponse {
        val trns = ArrayList<Trn>()
        for (trnInput in trnRequest.data) {
            trns.add(map(portfolio, trnInput))
        }
        return TrnResponse(trns)
    }

    fun map(portfolio: Portfolio, trnInput: TrnInput): Trn {

        return Trn(
            keyGenUtils.id,
            from(trnInput.callerRef, portfolio),
            trnInput.trnType,
            trnInput.status,
            portfolio,
            assetService.find(trnInput.assetId),
            if (trnInput.cashAsset == null) null else assetService.find(trnInput.cashAsset!!),
            currencyService.getCode(trnInput.tradeCurrency)!!,
            if (trnInput.cashCurrency == null) null else currencyService.getCode(trnInput.cashCurrency!!),
            trnInput.tradeDate,
            trnInput.settleDate,
            trnInput.quantity,
            trnInput.price,
            trnInput.fees,
            trnInput.tax,
            tradeCalculator.amount(trnInput),
            trnInput.cashAmount,
            trnInput.tradeCashRate,
            trnInput.tradeBaseRate,
            trnInput.tradePortfolioRate,
            "1",
            trnInput.comments
        )
    }

    fun hydrate(asset: Asset?): Asset? {
        return if (asset == null) {
            null
        } else assetService.find(asset.id)
    }
}
