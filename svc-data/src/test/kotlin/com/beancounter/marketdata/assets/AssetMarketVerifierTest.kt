package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AssetMarketVerifierTest {
    private val assetSearchService = mock<AssetSearchService>()
    private val marketService = mock<MarketService>()
    private val verifier = AssetMarketVerifier(assetSearchService, marketService)

    private fun market(
        code: String,
        eodhd: String? = null
    ) = Market(code = code, aliases = eodhd?.let { mapOf("eodhd" to it) } ?: emptyMap())

    private fun hit(
        symbol: String,
        market: String
    ) = AssetSearchResult(
        symbol = symbol,
        name = "n",
        type = "ETF",
        region = null,
        currency = "USD",
        market = market
    )

    @BeforeEach
    fun stubMarkets() {
        whenever(marketService.getMarket("US")).thenReturn(market("US", "US"))
        whenever(marketService.getMarket("NASDAQ")).thenReturn(market("NASDAQ", "US"))
        whenever(marketService.getMarket("LSE")).thenReturn(market("LSE", "LSE"))
        whenever(marketService.getMarket("AMS")).thenReturn(market("AMS", "AS"))
    }

    @Test
    fun `rejects when ticker is listed only on a different exchange`() {
        whenever(assetSearchService.search("VUAA", null))
            .thenReturn(AssetSearchResponse(listOf(hit("VUAA", "LSE"), hit("VUAA", "AMS"))))

        assertThatThrownBy { verifier.verify("VUAA", market("US", "US"), enrichedName = null) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("US")
            .hasMessageContaining("LSE")
    }

    @Test
    fun `allows US ticker the search reports on NASDAQ (same EODHD exchange)`() {
        // Regression: US / NASDAQ / NYSE / AMEX share the EODHD exchange "US", so a
        // ticker the search tags NASDAQ must NOT be rejected for a US create.
        whenever(assetSearchService.search("BRK.B", null))
            .thenReturn(AssetSearchResponse(listOf(hit("BRK.B", "NASDAQ"))))

        verifier.verify("BRK.B", market("US", "US"), enrichedName = null)
    }

    @Test
    fun `allows when the ticker is not found anywhere (new or private ticker)`() {
        whenever(assetSearchService.search(any(), anyOrNull()))
            .thenReturn(AssetSearchResponse(emptyList()))

        verifier.verify("NEWCO", market("US", "US"), enrichedName = null)
    }

    @Test
    fun `skips the lookup entirely when the asset was enriched`() {
        verifier.verify("AAPL", market("US", "US"), enrichedName = "Apple Inc")

        verify(assetSearchService, never()).search(any(), anyOrNull())
    }

    @Test
    fun `skips internal markets that have no exchange listing`() {
        verifier.verify("USD", market("CASH"), enrichedName = null)
        verifier.verify("MYHOUSE", market("PRIVATE"), enrichedName = null)

        verify(assetSearchService, never()).search(any(), anyOrNull())
    }

    @Test
    fun `fails open when the search provider errors`() {
        whenever(assetSearchService.search(any(), anyOrNull()))
            .thenThrow(RuntimeException("provider down"))

        // No throw — a search outage must never block asset creation.
        verifier.verify("VUAA", market("US", "US"), enrichedName = null)
        assertThat(true).isTrue()
    }
}