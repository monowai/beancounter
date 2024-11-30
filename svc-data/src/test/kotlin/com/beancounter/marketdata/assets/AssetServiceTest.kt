package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@SpringMvcDbTest
class AssetServiceTest {
    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    lateinit var marketService: MarketService

    @Autowired
    lateinit var marketDataService: MarketDataService

    @Test
    fun `resolveAssets should return PriceRequest with resolved assets`() {
        val aapl = "AAPL"
        val googl = "GOOGL"
        val assets =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        aapl to AssetInput(NASDAQ.code, aapl, name = "Apple Inc."),
                        googl to AssetInput(NASDAQ.code, googl, name = "Alphabet Inc."),
                    ),
                ),
            )
        val priceAssets =
            listOf(
                PriceAsset(NASDAQ.code, "AAPL", assetId = assets.data[aapl]!!.id),
                PriceAsset(NASDAQ.code, "GOOGL", assetId = assets.data[googl]!!.id),
            )
        val priceRequest = PriceRequest(assets = priceAssets)

        val result = assetService.resolveAssets(priceRequest)

        assertThat(result.assets).hasSize(2)
        assertThat(result.assets[0].resolvedAsset).isNotNull
            .hasNoNullFieldsOrPropertiesExcept("systemUser", "priceSymbol")
        assertThat(result.assets[1].resolvedAsset).isNotNull
            .hasNoNullFieldsOrPropertiesExcept("systemUser", "priceSymbol")

        // val byProviders = ProviderUtils().splitProviders(priceRequest.assets)
    }
}
