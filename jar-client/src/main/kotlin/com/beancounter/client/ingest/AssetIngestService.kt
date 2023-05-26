package com.beancounter.client.ingest

import com.beancounter.client.AssetService
import com.beancounter.client.MarketService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import org.springframework.stereotype.Service

/**
 * Write and return assets.
 */
@Service
class AssetIngestService internal constructor(
    private val assetService: AssetService,
    private val marketService: MarketService,
) {

    /**
     * Create assets, if necessary, and return the hydrated assets.
     *
     * @return hydrated asset with a primary key.
     */
    fun resolveAsset(assetInput: AssetInput): Asset {
        val market = marketService.getMarket(assetInput.market)
        val assetCategory = if (market.code.lowercase() == "cash") {
            "Cash"
        } else if (market.code.lowercase() == "re") {
            "RE"
        } else {
            "Equity"
        }

        val assetRequest = AssetRequest(
            mapOf(
                Pair(
                    toKey(assetInput.code, market.code),
                    AssetInput(
                        market.code,
                        code = assetInput.code,
                        name = assetInput.name,
                        category = assetCategory,
                        owner = assetInput.owner,
                    ),
                ),
            ),
        )
        val response = assetService.handle(assetRequest)
            ?: throw BusinessException(
                String.format(
                    "No response returned for %s:%s",
                    assetInput.code,
                    assetInput.market,
                ),
            )
        return response.data.values.iterator().next()
    }
}
