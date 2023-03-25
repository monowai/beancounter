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
     * @param marketCode exchange code
     * @param assetCode  Code on the exchange
     * @return hydrated asset with a primary key.
     */
    fun resolveAsset(marketCode: String, assetCode: String, name: String? = null): Asset {
        val market = marketService.getMarket(marketCode)
        val callerKey = toKey(assetCode, market.code)

        val assets = HashMap<String, AssetInput>()
        assets[callerKey] = AssetInput(market.code, code = assetCode, name = name)
        val assetRequest = AssetRequest(assets)
        val response = assetService.handle(assetRequest)
            ?: throw BusinessException(String.format("No response returned for %s:%s", assetCode, marketCode))
        return response.data.values.iterator().next()
    }
}
