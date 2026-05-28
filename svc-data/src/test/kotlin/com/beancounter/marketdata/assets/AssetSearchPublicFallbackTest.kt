package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.assets.figi.FigiAsset
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiFilterResponse
import com.beancounter.marketdata.assets.figi.FigiFilterResult
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiResponse
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataPriceProvider
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.marketstack.MarketStackConfig
import com.beancounter.marketdata.providers.marketstack.MarketStackGateway
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Verifies the FIGI -> AlphaVantage fallback for partial public-market
 * ticker searches. FIGI's /v3/search returns mostly options/derivatives
 * for short ticker prefixes (e.g. "COW"), all of which we filter out,
 * leaving the user with "No results found" while typing. Falling back
 * to AlphaVantage SYMBOL_SEARCH gives them fuzzy matches as they type.
 */
class AssetSearchPublicFallbackTest {
    private val usMarket = Market(code = "US", currency = Currency("USD"))

    private val alphaJson =
        """
        {"data":[
          {"symbol":"COWZ","name":"PACER US CASH COWS 100 ETF","type":"ETF",
           "region":"United States","currency":"USD","market":"US"}
        ]}
        """.trimIndent()

    private fun buildService(
        figiTickerHits: Collection<FigiResponse> = emptyList(),
        figiFilterHits: FigiFilterResponse = FigiFilterResponse(),
        alphaResponse: String = alphaJson,
        alphaProxy: AlphaProxy = mock { on { search(any(), any()) } doReturn alphaResponse },
        marketProvider: MarketDataPriceProvider =
            mock { on { searchAssets(any(), anyOrNull()) } doReturn emptyList() },
        allProviders: Collection<MarketDataPriceProvider> = listOf(marketProvider)
    ): Pair<AssetSearchService, AlphaProxy> {
        val alphaConfig =
            mock<AlphaConfig> {
                on { isNullMarket(any()) } doReturn true
                on { translateSymbol(any()) } doReturn "COW"
                on { getObjectMapper() } doReturn BcJson.objectMapper
            }
        val figiGateway =
            mock<FigiGateway> {
                on { search(any(), any()) } doReturn figiTickerHits
                on { filter(any(), any()) } doReturn figiFilterHits
            }
        val figiConfig =
            FigiConfig().apply {
                apiKey = "test"
                enabled = true
                searchMarkets = "US"
            }
        val mdFactory =
            mock<MdFactory> {
                on { getMarketDataProvider(any<Market>()) } doReturn marketProvider
                on { getAllProviders() } doReturn allProviders
            }
        val service =
            AssetSearchService(
                assetRepository =
                    mock { on { searchByCodeOrName(any()) } doReturn emptyList() },
                alphaProxy = alphaProxy,
                alphaConfig = alphaConfig,
                marketStackGateway = mock<MarketStackGateway>(),
                marketStackConfig =
                    mock {
                        on { markets } doReturn ""
                        on { apiKey } doReturn "test"
                    },
                figiGateway = figiGateway,
                figiConfig = figiConfig,
                marketService =
                    mock<MarketService> { on { getMarket(any()) } doReturn usMarket },
                systemUserService = mock<SystemUserService>(),
                mdFactory = mdFactory
            )
        return service to alphaProxy
    }

    @Test
    fun `falls back to AlphaVantage when FIGI returns no allowed results`() {
        val (service, alphaProxy) = buildService()

        val results = service.search("COW", "US")

        assertThat(results.data)
            .extracting<String> { it.symbol }
            .containsExactly("COWZ")
        verify(alphaProxy).search(any(), any())
    }

    @Test
    fun `searchFigiGlobal drops results whose exchCode does not map to a BC market`() {
        val figiFilterHits =
            FigiFilterResponse(
                data =
                    listOf(
                        // VG / UF / UT / UC are not in FigiConfig.EXCHANGE_CODES.
                        // Returning them raw made the lookup UI post unknown
                        // market codes to /api/assets and trigger a 404.
                        FigiFilterResult(
                            ticker = "GCOW",
                            name = "PACER GLOBAL CASH COWS DIVID",
                            exchCode = "VG",
                            securityType2 = MUTUAL_FUND
                        ),
                        FigiFilterResult(
                            ticker = "GCOW",
                            name = "PACER GLOBAL CASH COWS DIVID",
                            exchCode = "UF",
                            securityType2 = MUTUAL_FUND
                        ),
                        FigiFilterResult(
                            ticker = "COWZ",
                            name = "PACER US CASH COWS 100 ETF",
                            exchCode = "US",
                            securityType2 = MUTUAL_FUND
                        )
                    )
            )
        val (service, _) =
            buildService(
                figiFilterHits = figiFilterHits,
                alphaProxy = mock<AlphaProxy>()
            )

        val results = service.search("COW", "FIGI")

        assertThat(results.data)
            .extracting<String> { it.market }
            .containsExactly("US")
        assertThat(results.data.first().symbol).isEqualTo("COWZ")
    }

    @Test
    fun `does not call AlphaVantage when FIGI ticker mapping returns matches`() {
        val tickerHit =
            FigiResponse(
                data =
                    listOf(
                        FigiAsset(
                            ticker = "COWZ",
                            name = "PACER US CASH COWS 100 ETF",
                            securityType2 = "Mutual Fund"
                        )
                    ),
                error = null
            )
        val alphaProxy = mock<AlphaProxy>()
        val (service, _) = buildService(figiTickerHits = listOf(tickerHit), alphaProxy = alphaProxy)

        val results = service.search("COWZ", "US")

        assertThat(results.data).isNotEmpty
        assertThat(results.data.first().symbol).isEqualTo("COWZ")
        verify(alphaProxy, never()).search(any(), any())
    }

    @Test
    fun `market search routes through configured price provider before legacy chain`() {
        val provider =
            mock<MarketDataPriceProvider> {
                on { searchAssets(any(), anyOrNull()) } doReturn
                    listOf(
                        AssetSearchResult(
                            symbol = "HYSA",
                            name = HYSA_NAME,
                            type = "ETF",
                            region = "US",
                            currency = "USD",
                            market = "US"
                        )
                    )
            }
        val alphaProxy = mock<AlphaProxy>()
        val (service, _) = buildService(marketProvider = provider, alphaProxy = alphaProxy)

        val results = service.search("HYSA", "US")

        assertThat(results.data)
            .extracting<String> { it.symbol }
            .containsExactly("HYSA")
        verify(provider).searchAssets(eq("HYSA"), eq("US"))
        verify(alphaProxy, never()).search(any(), any())
    }

    @Test
    fun `null-market search fans out across all registered providers`() {
        val provider =
            mock<MarketDataPriceProvider> {
                on { searchAssets(any(), anyOrNull()) } doReturn
                    listOf(
                        AssetSearchResult(
                            symbol = "HYSA",
                            name = HYSA_NAME,
                            type = "ETF",
                            region = "US",
                            currency = "USD",
                            market = "US"
                        )
                    )
            }
        val alphaProxy = mock<AlphaProxy>()
        val (service, _) =
            buildService(
                marketProvider =
                    mock { on { searchAssets(any(), anyOrNull()) } doReturn emptyList() },
                allProviders = listOf(provider),
                alphaProxy = alphaProxy
            )

        val results = service.search("HYSA", null)

        assertThat(results.data)
            .extracting<String> { it.symbol }
            .contains("HYSA")
        // Forwarded market must be null — anyOrNull() would mask a leaked non-null.
        verify(provider).searchAssets(eq("HYSA"), isNull())
        // Header-bar search must not hit AlphaVantage as a side effect of the fan-out.
        verify(alphaProxy, never()).search(any(), any())
    }

    @Test
    fun `null-market fan-out returns US-market hits before non-US`() {
        val provider =
            mock<MarketDataPriceProvider> {
                on { searchAssets(any(), anyOrNull()) } doReturn
                    listOf(
                        AssetSearchResult(
                            symbol = "ABC",
                            name = "ABC London",
                            type = "Equity",
                            region = "LON",
                            currency = "GBP",
                            market = "LON"
                        ),
                        AssetSearchResult(
                            symbol = "ABC",
                            name = "ABC US",
                            type = "Equity",
                            region = "US",
                            currency = "USD",
                            market = "US"
                        )
                    )
            }
        val (service, _) =
            buildService(
                marketProvider =
                    mock { on { searchAssets(any(), anyOrNull()) } doReturn emptyList() },
                allProviders = listOf(provider)
            )

        val results = service.search("ABC", null)

        assertThat(results.data)
            .extracting<String> { it.market }
            .containsExactly("US", "LON")
    }

    @Test
    fun `null-market fan-out keeps healthy provider results when sibling throws`() {
        // Regression guard for the supervisorScope wrapper: a thrown provider must not
        // cancel siblings or propagate up. Real-world trigger: EODHD connect-timeout while
        // FIGI/Alpha are healthy.
        val healthy =
            mock<MarketDataPriceProvider> {
                on { searchAssets(any(), anyOrNull()) } doReturn
                    listOf(
                        AssetSearchResult(
                            symbol = "HYSA",
                            name = HYSA_NAME,
                            type = "ETF",
                            region = "US",
                            currency = "USD",
                            market = "US"
                        )
                    )
            }
        val broken =
            mock<MarketDataPriceProvider> {
                on { searchAssets(any(), anyOrNull()) } doThrow RuntimeException("upstream down")
                on { getId() } doReturn "BROKEN"
            }
        val (service, _) =
            buildService(
                marketProvider =
                    mock { on { searchAssets(any(), anyOrNull()) } doReturn emptyList() },
                allProviders = listOf(broken, healthy)
            )

        val results = service.search("HYSA", null)

        assertThat(results.data)
            .extracting<String> { it.symbol }
            .contains("HYSA")
    }

    companion object {
        private const val MUTUAL_FUND = "Mutual Fund"
        private const val HYSA_NAME = "Pacer International HY Corp Bond ETF"
    }
}