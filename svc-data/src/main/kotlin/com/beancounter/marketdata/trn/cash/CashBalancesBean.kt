package com.beancounter.marketdata.trn.cash

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration

/**
 * Persists the currency objects for each supported market.
 */
@Configuration
class CashBalancesBean(
    val currencyService: CurrencyService,
    val assetService: AssetService
) {
    @Autowired
    fun createCashBalanceAssets(marketConfig: MarketConfig): AssetUpdateResponse {
        val assets = mutableMapOf<String, AssetInput>()
        for (currency in currencyService.currencies) {
            assets[currency.code] = AssetUtils.getCash(currency.code)
        }

        val assetRequest = AssetRequest(assets)
        return assetService.handle(assetRequest)
    }
}