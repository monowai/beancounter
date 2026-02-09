package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AccountingTypeService
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service

/**
 * Bloomberg OpenFigi support for asset enrichment.
 */
@Service
class FigiAdapter(
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService
) {
    fun transform(
        market: Market,
        assetCode: String,
        defaultName: String? = null,
        id: String,
        category: String = "Equity"
    ): Asset {
        val currency = currencyService.getCode(market.currencyId)
        val accountingType =
            accountingTypeService.getOrCreate(
                category = category,
                currency = currency
            )
        return Asset(
            code = assetCode,
            id = id,
            name = defaultName,
            market = market,
            marketCode = market.code,
            priceSymbol = assetCode,
            category = category,
            accountingType = accountingType
        )
    }

    fun transform(
        market: Market,
        assetCode: String,
        figiAsset: FigiAsset,
        id: String
    ): Asset =
        transform(
            market,
            assetCode,
            defaultName = figiAsset.name,
            id = id,
            category = figiAsset.securityType2
        )
}