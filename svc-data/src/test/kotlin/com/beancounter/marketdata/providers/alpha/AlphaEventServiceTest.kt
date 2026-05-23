package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MarketDataRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Tests for AlphaEventService - ensures unsupported markets
 * are filtered out before making API calls.
 */
class AlphaEventServiceTest {
    private val assetFinder = mock(AssetFinder::class.java)
    private val alphaGateway = mock(AlphaGateway::class.java)
    private val alphaConfig = mock(AlphaConfig::class.java)
    private val assetService = mock(AssetService::class.java)
    private val marketDataRepo = mock(MarketDataRepo::class.java)

    private val alphaEventService =
        AlphaEventService(assetFinder, alphaGateway, alphaConfig, assetService, marketDataRepo)

    @Test
    fun `should return empty response for PRIVATE market assets`() {
        // Given: A PRIVATE market asset
        val privateMarket = Market("PRIVATE")
        val privateAsset = Asset(id = "test-id", code = "2xstJu-9QzaiX_Ou1U2Ssw.DBS-DIGI", market = privateMarket)

        // And: Alpha only supports US markets
        `when`(alphaConfig.markets).thenReturn("US,NASDAQ,AMEX,NYSE,LON")

        // When: Getting events for the private asset
        val result = alphaEventService.getEvents(privateAsset)

        // Then: Should return empty response
        assertThat(result.data).isEmpty()

        // And: Should NOT call the Alpha gateway
        verify(alphaGateway, never()).getAdjusted(privateAsset.code, "")
    }

    @Test
    fun `should return empty response for CASH market assets`() {
        // Given: A CASH market asset
        val cashMarket = Market("CASH")
        val cashAsset = Asset(id = "usd-id", code = "USD", market = cashMarket)

        // And: Alpha only supports stock markets
        `when`(alphaConfig.markets).thenReturn("US,NASDAQ,AMEX,NYSE,LON")

        // When: Getting events for the cash asset
        val result = alphaEventService.getEvents(cashAsset)

        // Then: Should return empty response
        assertThat(result.data).isEmpty()

        // And: Should NOT call the Alpha gateway
        verify(alphaGateway, never()).getAdjusted(cashAsset.code, "")
    }

    @Test
    fun `should query stored prices for NZX market assets`() {
        // Given: An NZX asset (NZX not in Alpha's supported markets)
        val nzxMarket = Market("NZX")
        val nzxAsset = Asset(id = "gne-id", code = "GNE", market = nzxMarket)

        // And: Alpha does not include NZX in its supported markets
        `when`(alphaConfig.markets).thenReturn("US,NASDAQ,AMEX,NYSE,LON")

        // When: Getting events for the NZX asset
        alphaEventService.getEvents(nzxAsset)

        // Then: Should query stored prices from the database
        verify(marketDataRepo).findEventsByAssetId(nzxAsset.id)

        // And: Should NOT call the Alpha gateway
        verify(alphaGateway, never()).getAdjusted(nzxAsset.code, "")
    }

    @Test
    fun `should return empty response when markets config is null`() {
        // Given: An asset with any market
        val market = Market("US")
        val asset = Asset(id = "test-id", code = "TEST", market = market)

        // And: Alpha markets config is null
        `when`(alphaConfig.markets).thenReturn(null)

        // When: Getting events for the asset
        val result = alphaEventService.getEvents(asset)

        // Then: Should return empty response
        assertThat(result.data).isEmpty()

        // And: Should NOT call the Alpha gateway
        verify(alphaGateway, never()).getAdjusted(asset.code, "")
    }
}