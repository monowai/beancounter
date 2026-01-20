package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.CallerRef.Companion.from
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.TradeCalculator
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Map TrnInput objects to the Trn persistent model.
 */
@Service
class TrnInputMapper(
    private val assetFinder: AssetFinder,
    var currencyService: CurrencyService,
    var tradeCalculator: TradeCalculator,
    val cashTrnServices: CashTrnServices,
    val fxTransactions: FxTransactions,
    val keyGenUtils: KeyGenUtils,
    val brokerRepository: BrokerRepository
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

        val cashAsset = cashTrnServices.getCashAsset(trnInput.trnType, trnInput.cashAssetId, trnInput.cashCurrency)
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

        // COST_ADJUST has no quantity impact - keep it as zero
        val rawQuantity =
            if (trnInput.trnType == TrnType.COST_ADJUST) {
                BigDecimal.ZERO
            } else if (trnInput.quantity == BigDecimal.ZERO) {
                tradeAmount
            } else {
                trnInput.quantity
            }
        // Enforce correct sign for cash transactions based on type
        val quantity =
            if (TrnType.isCash(trnInput.trnType)) {
                if (TrnType.isCashCredited(trnInput.trnType)) {
                    rawQuantity.abs()
                } else {
                    rawQuantity.abs().negate()
                }
            } else {
                rawQuantity
            }
        val cashAmount =
            cashTrnServices.getCashImpact(
                trnInput,
                tradeAmount
            )
        val asset = getAsset(trnInput, existing)
        val tradeCurrency =
            if (trnInput.tradeCurrency.isEmpty()) {
                // For CASH/PRIVATE markets, use priceSymbol which stores the currency
                // For other markets, use the market's default currency
                when (asset.market.code) {
                    "CASH", "PRIVATE" -> currencyService.getCode(asset.priceSymbol ?: asset.market.currency.code)
                    else -> asset.market.currency
                }
            } else {
                currencyService.getCode(
                    trnInput.tradeCurrency
                )
            }
        val broker = getBroker(trnInput, existing)
        return Trn(
            id = existing?.id ?: keyGenUtils.id,
            trnType = trnInput.trnType,
            tradeDate = trnInput.tradeDate,
            asset = asset,
            quantity = quantity,
            callerRef = existing?.callerRef ?: from(trnInput.callerRef),
            price = trnInput.price,
            tradeAmount = tradeAmount,
            tradeCurrency = tradeCurrency,
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
            comments = trnInput.comments ?: existing?.comments,
            broker = broker,
            status = trnInput.status
        )
    }

    private fun getBroker(
        trnInput: TrnInput,
        existing: Trn?
    ): Broker? {
        val brokerId = trnInput.brokerId
        return if (brokerId != null) {
            brokerRepository.findById(brokerId).orElse(null)
        } else {
            existing?.broker
        }
    }

    private fun getAsset(
        trnInput: TrnInput,
        existing: Trn?
    ): Asset {
        val asset = existing?.asset ?: assetFinder.find(trnInput.assetId!!)
        return asset
    }
}