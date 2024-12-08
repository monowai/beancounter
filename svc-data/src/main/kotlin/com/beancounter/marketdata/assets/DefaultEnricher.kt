package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Fallback enrichment behaviour that creates asset objets based on the input.
 *
 * There are no external dependencies for this Enricher.
 */
@Service
class DefaultEnricher : AssetEnricher {
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput,
    ): Asset =
        Asset(
            code = assetInput.code.uppercase(Locale.getDefault()),
            id = id,
            name =
            if (assetInput.name != null) {
                assetInput.name!!.replace(
                    "\"",
                    "",
                )
            } else {
                null
            },
            market = market,
            marketCode = market.code,
            priceSymbol = assetInput.currency,
            category = assetInput.category,
        )

    override fun canEnrich(asset: Asset): Boolean = true

    override fun id(): String = "DEFAULT"
}
