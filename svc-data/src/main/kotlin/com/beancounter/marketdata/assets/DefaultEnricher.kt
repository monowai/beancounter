package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.currency.CurrencyService
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Fallback enrichment behaviour that creates asset objects based on the input.
 *
 * There are no external dependencies for this Enricher.
 */
@Service
class DefaultEnricher(
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService
) : AssetEnricher {
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput
    ): Asset {
        val currencyCode = assetInput.currency ?: market.currencyId
        val currency = currencyService.getCode(currencyCode)
        val accountingType =
            accountingTypeService.getOrCreate(
                category = assetInput.category,
                currency = currency
            )
        return Asset(
            code = assetInput.code.uppercase(Locale.getDefault()),
            id = id,
            name =
                assetInput.name?.replace(
                    "\"",
                    ""
                ),
            market = market,
            marketCode = market.code,
            category = assetInput.category,
            accountingType = accountingType
        )
    }

    override fun canEnrich(asset: Asset): Boolean = true

    override fun id(): String = "DEFAULT"
}