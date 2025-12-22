package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.marketstack.model.MarketStackData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Async proxy to obtain MarketData.
 */
@Service
class MarketStackProxy(
    val marketStackAdapter: MarketStackAdapter,
    private val marketStackGateway: MarketStackGateway
) {
    fun getPrices(
        providerArguments: ProviderArguments,
        batch: Int,
        apiKey: String
    ): MarketStackResponse {
        val assets = providerArguments.batch[batch]!!
        val marketOpenDate = providerArguments.getBatchConfigs(batch)?.date!!
        val response =
            marketStackGateway.getPrices(
                assets,
                marketOpenDate,
                apiKey
            )
        val missingPrices = mutableListOf<MarketStackData>()
        if (response.data.size != assets.split(",").size) {
            val missingAssets =
                assets.split(",").filter { assetCode ->
                    response.data.none { marketStackData: MarketStackData ->
                        marketStackData.symbol == assetCode
                    }
                }
            missingAssets.forEach { assetCode ->
                missingPrices.add(
                    marketStackAdapter.getMsDefault(
                        assetCode,
                        "",
                        LocalDateTime.now()
                    )
                )
            }
        }
        return MarketStackResponse(
            response.data + missingPrices,
            response.error
        )
    }
}