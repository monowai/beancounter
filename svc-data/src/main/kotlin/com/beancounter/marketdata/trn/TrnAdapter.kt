package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef.Companion.from
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.trn.cash.CashServices
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Map TrnInput objects to the Trn persistent model.
 */
@Service
class TrnAdapter(
    var assetService: AssetService,
    var currencyService: CurrencyService,
    var tradeCalculator: TradeCalculator,
    val cashServices: CashServices,
    val fxTransactions: FxTransactions,
    val keyGenUtils: KeyGenUtils
) {
    fun convert(
        portfolio: Portfolio,
        trnRequest: TrnRequest
    ): List<Trn> =
        trnRequest.data.map { trnInput ->
            map(
                portfolio = portfolio,
                trnInput = trnInput
            )
        }

    fun map(
        portfolio: Portfolio,
        trnInput: TrnInput,
        existing: Trn? = null
    ): Trn {
        fxTransactions.setRates(
            portfolio,
            trnInput
        )

        val cashAsset = cashServices.getCashAsset(trnInput.trnType, trnInput.cashAssetId, trnInput.cashCurrency)
        var cashCurrency: Currency? = null
        if (cashAsset != null) {
            cashCurrency =
                if (cashAsset.priceSymbol == null) {
                    currencyService.getCode(trnInput.cashCurrency!!)
                } else {
                    currencyService.getCode(cashAsset.priceSymbol!!)
                }
        }
        val tradeAmount = tradeCalculator.amount(trnInput)
        val tradeCashRate = tradeCalculator.cashFxRate(tradeAmount, trnInput)

        val quantity = if (trnInput.quantity == BigDecimal.ZERO) tradeAmount else trnInput.quantity
        val cashAmount =
            cashServices.getCashImpact(
                trnInput,
                tradeAmount
            )

        return Trn(
            id = existing?.id ?: keyGenUtils.id,
            trnType = trnInput.trnType,
            tradeDate = trnInput.tradeDate,
            asset = existing?.asset ?: assetService.find(trnInput.assetId!!),
            quantity = quantity,
            callerRef = existing?.callerRef ?: from(trnInput.callerRef),
            price = trnInput.price,
            tradeAmount = tradeAmount,
            tradeCurrency = currencyService.getCode(trnInput.tradeCurrency),
            cashAsset = cashAsset,
            cashCurrency = cashCurrency,
            tradeCashRate = tradeCashRate,
            tradeBaseRate = trnInput.tradeBaseRate,
            tradePortfolioRate = trnInput.tradePortfolioRate,
            cashAmount = cashAmount,
            portfolio = portfolio,
            // Sign this value
            settleDate = trnInput.settleDate,
            fees = trnInput.fees,
            tax = trnInput.tax,
            comments = existing?.comments ?: trnInput.comments,
            status = trnInput.status
        )
    }
}