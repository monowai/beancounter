package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef.Companion.from
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.key.KeyGenUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
            trns.add(map(portfolio = portfolio, trnInput = trnInput))
        }
        return TrnResponse(trns)
    }

    fun map(portfolio: Portfolio, trnInput: TrnInput, existing: Trn? = null): Trn {
        val cashAsset = cashServices.getCashAsset(trnInput)
        var cashCurrency: Currency? = null
        if (cashAsset != null) {
            cashCurrency = currencyService.getCode(cashAsset.priceSymbol!!)
        }
        val tradeAmount = tradeCalculator.amount(trnInput)
        val quantity = if (trnInput.quantity == BigDecimal.ZERO) tradeAmount else trnInput.quantity
        return Trn(
            id = existing?.id ?: keyGenUtils.id,
            callerRef = existing?.callerRef ?: from(trnInput.callerRef, portfolio),
            trnType = trnInput.trnType,
            portfolio = portfolio,
            asset = existing?.asset ?: assetService.find(trnInput.assetId!!),
            tradeCurrency = currencyService.getCode(trnInput.tradeCurrency)!!,
            cashCurrency = cashCurrency,
            cashAsset = cashAsset,
            tradeDate = trnInput.tradeDate,
            settleDate = trnInput.settleDate,
            price = trnInput.price,
            fees = trnInput.fees,
            tax = trnInput.tax,
            tradeAmount = tradeAmount,
            status = trnInput.status,
            cashAmount = cashServices.getCashImpact(trnInput, tradeAmount),
            // Sign this value
            quantity = quantity,
            tradeCashRate = trnInput.tradeCashRate,
            tradeBaseRate = trnInput.tradeBaseRate,
            tradePortfolioRate = trnInput.tradePortfolioRate,
            comments = existing?.comments ?: trnInput.comments
        )
    }

    fun hydrate(asset: Asset?): Asset? {
        return if (asset == null) {
            null
        } else assetService.find(asset.id)
    }
}
