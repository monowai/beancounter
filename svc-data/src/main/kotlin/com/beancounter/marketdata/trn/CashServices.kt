package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Cash calculation services for a pre-populated TrnInput.
 */
@Service
class CashServices(val assetService: AssetService, val currencyService: CurrencyService) {
    private val creditsCash = arrayOf(TrnType.DEPOSIT, TrnType.SELL, TrnType.DIVI)
    private val debitsCash = arrayOf(TrnType.BUY, TrnType.WITHDRAWAL)

    fun getCashImpact(trnInput: TrnInput): BigDecimal? {
        if (trnInput.cashAmount != null) {
            return trnInput.cashAmount
        }
        if (TrnType.isCashImpacted(trnInput.trnType)) {
            val rate = trnInput.tradeCashRate ?: BigDecimal("1.00")
            if (creditsCash.contains(trnInput.trnType)) {
                return MathUtils.multiply(trnInput.tradeAmount.abs(), rate)
            } else if (debitsCash.contains(trnInput.trnType)) {
                return MathUtils.multiply(BigDecimal.ZERO.minus(trnInput.tradeAmount.abs()), rate)
            }
        }
        return BigDecimal.ZERO
    }

    fun getCashAsset(trnInput: TrnInput): Asset? {
        if (!TrnType.isCashImpacted(trnInput.trnType)) {
            return null // No cash asset required
        }
        if (trnInput.cashAssetId == null) {
            if (trnInput.cashCurrency == null) {
                return null // no cash to look up.
            }
            // Generic Cash Balance
            return assetService.find("CASH", "${trnInput.cashCurrency} Balance")
        }
        return assetService.find(trnInput.cashAssetId!!)
    }

    @Autowired
    fun createCashBalanceAssets(marketConfig: MarketConfig): AssetUpdateResponse {
        val assets = mutableMapOf<String, AssetInput>()
        for (currency in currencyService.currencies) {
            assets[currency.code] = AssetInput(
                marketConfig.getProviders()["CASH"]!!.code,
                "${currency.code} Balance", currency = currency.code
            )
        }

        val assetRequest = AssetRequest(assets)
        return assetService.process(assetRequest)
    }
}
