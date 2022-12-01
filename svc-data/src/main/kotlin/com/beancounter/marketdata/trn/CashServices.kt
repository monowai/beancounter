package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import com.beancounter.common.model.TrnType.Companion.creditsCash
import com.beancounter.common.model.TrnType.Companion.debitsCash
import com.beancounter.common.utils.AssetUtils
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

    fun getCashImpact(trnInput: TrnInput, tradeAmount: BigDecimal = trnInput.tradeAmount): BigDecimal {
        if (TrnType.isCashImpacted(trnInput.trnType)) {
            if (trnInput.cashAmount.compareTo(BigDecimal.ZERO) != 0) {
                return trnInput.cashAmount // Cash amount has been set by the caller
            }
            val rate = trnInput.tradeCashRate ?: BigDecimal("1.00")
            if (creditsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(tradeAmount.abs(), rate)
            } else if (debitsCash.contains(trnInput.trnType)) {
                return MathUtils.divide(BigDecimal.ZERO.minus(tradeAmount.abs()), rate)
            }
        }
        return BigDecimal.ZERO
    }

    fun getCashAsset(trnType: TrnType, cashAssetId: String?, cashCurrency: String?): Asset? {
        if (!TrnType.isCashImpacted(trnType)) {
            return null // No cash asset required
        }
        if (cashAssetId.isNullOrEmpty()) {
            if (cashCurrency.isNullOrEmpty()) {
                return null // no cash to look up.
            }
            // Generic Cash Balance
            return assetService.find(cash, cashCurrency)
        }
        return assetService.find(cashAssetId)
    }

    fun getCashAsset(trnInput: TrnInput): Asset? {
        return getCashAsset(trnInput.trnType, trnInput.cashAssetId, trnInput.cashCurrency)
    }

    @Autowired
    fun createCashBalanceAssets(marketConfig: MarketConfig): AssetUpdateResponse {
        val assets = mutableMapOf<String, AssetInput>()
        for (currency in currencyService.currencies) {
            assets[currency.code] = AssetUtils.getCash(currency.code)
        }

        val assetRequest = AssetRequest(assets)
        return assetService.handle(assetRequest)
    }

    companion object {
        const val cash = "CASH"
    }
}
