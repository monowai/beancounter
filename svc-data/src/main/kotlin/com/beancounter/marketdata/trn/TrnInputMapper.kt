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

        val broker = getBroker(trnInput, existing)
        val asset = getAsset(trnInput, existing)

        // A broker was supplied but no explicit settlement account/currency —
        // default the settlement currency to the trade currency so
        // CashTrnServices.getCashAsset can resolve the broker's per-currency
        // settlement account. Without this, brokerId-only SELL/BUY/etc saves
        // resolve cashAsset = null and CashAutoSettleService silently skips
        // the compensating transfer (#1040). FX_BUY is excluded — its cash
        // leg currency is the SOLD currency, not the trade (bought) currency,
        // so defaulting here would corrupt it.
        val effectiveCashCurrency =
            if (trnInput.cashCurrency.isNullOrEmpty() &&
                trnInput.cashAssetId.isNullOrEmpty() &&
                broker != null &&
                TrnType.isCashImpacted(trnInput.trnType) &&
                !TrnType.isCash(trnInput.trnType) &&
                trnInput.trnType != TrnType.FX_BUY
            ) {
                resolveTradeCurrency(asset, trnInput.tradeCurrency).code
            } else {
                trnInput.cashCurrency
            }

        // Preserve existing cashAsset if no new one is provided (similar to broker handling)
        val cashAsset =
            cashTrnServices.getCashAsset(
                trnInput.trnType,
                trnInput.cashAssetId,
                effectiveCashCurrency,
                portfolio.owner.id,
                broker?.id
            ) ?: existing?.cashAsset
        var cashCurrency: Currency? = null
        if (cashAsset != null) {
            cashCurrency =
                when {
                    // Use explicitly provided cashCurrency
                    !trnInput.cashCurrency.isNullOrEmpty() -> currencyService.getCode(trnInput.cashCurrency!!)
                    // Preserve existing cashCurrency when patching
                    existing?.cashCurrency != null -> existing.cashCurrency
                    // Use the cash asset's class currency
                    else -> cashAsset.accountingType?.currency ?: cashAsset.market.currency
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
        val tradeCurrency = resolveTradeCurrency(asset, trnInput.tradeCurrency)
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
            status = trnInput.status,
            modelId = trnInput.modelId ?: existing?.modelId,
            subAccounts = trnInput.subAccounts ?: existing?.subAccounts
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

    /**
     * Composite policy assets (CPF today, ILP later) are denominated in a
     * statutory currency carried on the asset's accountingType. The market
     * is "PRIVATE" with a placeholder currency that must NOT leak into the
     * trn — otherwise svc-position multiplies by the wrong FX rate when
     * synthesising the PORTFOLIO / BASE buckets. Server-side override is
     * the defense for any caller (CSV import, retry of an old payload, a
     * client that forgot to set tradeCurrency correctly).
     *
     * For non-POLICY assets the historical behaviour stands: honour the
     * caller's tradeCurrency when supplied, fall back to the asset's
     * accounting/market currency otherwise.
     */
    private fun resolveTradeCurrency(
        asset: Asset,
        requestedCode: String
    ): com.beancounter.common.model.Currency {
        val accountingCurrency = asset.accountingType?.currency
        if (asset.assetCategory.id == "POLICY" && accountingCurrency != null) {
            return accountingCurrency
        }
        return if (requestedCode.isEmpty()) {
            accountingCurrency ?: asset.market.currency
        } else {
            currencyService.getCode(requestedCode)
        }
    }
}