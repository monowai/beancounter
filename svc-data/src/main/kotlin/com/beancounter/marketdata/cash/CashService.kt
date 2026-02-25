package com.beancounter.marketdata.cash

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

const val CASH = "CASH"

/**
 * Cash Management Services.
 */
@Service
@Transactional
class CashService(
    private val assetFinder: AssetFinder,
    val currencyService: CurrencyService,
    val assetService: AssetService
) {
    val assets = mutableMapOf<String, AssetInput>()

    fun createCashBalanceAssets() {
        if (assets.isEmpty()) {
            for (currency in currencyService.currencies()) {
                assets[currency.code] = AssetUtils.getCash(currency.code)
            }

            val assetRequest = AssetRequest(assets)
            assetService.handle(assetRequest)
        }
    }

    fun find() = assetFinder.findByMarketCode(CASH)
}