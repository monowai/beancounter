package com.beancounter.client.ingest

import com.beancounter.client.AssetService
import com.beancounter.client.MarketService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import org.springframework.stereotype.Service

@Service
/**
 * Write and return assets.
 */
class AssetIngestService internal constructor(private val assetService: AssetService, private val marketService: MarketService) {

    /**
     * Create assets, if necessary, and return the hydrated assets.
     *
     * @param marketCode exchange code
     * @param assetCode  Code on the exchange
     * @return hydrated asset with a primary key.
     */
    fun resolveAsset(marketCode: String, assetCode: String): Asset {
        val market = marketService.getMarket(marketCode)
        if (market.inMemory()) {
            // Support unit testings where we don't really care about the asset
            return getAsset("MOCK", assetCode)
        }
        val callerKey = toKey(assetCode, market.code)

        val assets = HashMap<String, AssetInput>()
        assets[callerKey] = AssetInput(market.code, assetCode)
        val assetRequest = AssetRequest(assets)
        val response = assetService.process(assetRequest)
            ?: throw BusinessException(String.format("No response returned for %s:%s", assetCode, marketCode))
        return response.data.values.iterator().next()
    }
}
