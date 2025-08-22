package com.beancounter.marketdata.trn

import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import com.beancounter.common.model.TrnType.Companion.creditsCash
import com.beancounter.common.model.TrnType.Companion.debitsCash
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.cash.CASH
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Cash calculation services for a pre-populated TrnInput.
 */
@Service
class CashTrnServices(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    val currencyService: CurrencyService
) {
    fun getCashImpact(
        trnInput: TrnInput,
        tradeAmount: BigDecimal = trnInput.tradeAmount
    ): BigDecimal {
        if (TrnType.isCashImpacted(trnInput.trnType)) {
            if (trnInput.cashAmount.compareTo(BigDecimal.ZERO) != 0) {
                return trnInput.cashAmount // Cash amount has been set by the caller
            }
            val rate = trnInput.tradeCashRate
            if (creditsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(
                    tradeAmount.abs(),
                    rate
                )
            } else if (debitsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(
                    BigDecimal.ZERO.minus(tradeAmount.abs()),
                    rate
                )
            }
        }
        return BigDecimal.ZERO
    }

    fun getCashAsset(
        trnType: TrnType,
        cashAssetId: String?,
        cashCurrency: String?
    ): Asset? {
        if (!TrnType.isCashImpacted(trnType)) {
            return null // No cash asset required
        }
        if (cashAssetId.isNullOrEmpty()) {
            if (cashCurrency.isNullOrEmpty()) {
                return null // no cash to look up.
            }
            // Generic Cash Balance
            return assetService.findOrCreate(
                AssetInput(
                    CASH,
                    cashCurrency
                )
            )
        }
        return assetFinder.find(cashAssetId)
    }
}