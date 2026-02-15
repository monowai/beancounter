package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.figi.FigiEnricher
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEnricher
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.util.Locale

/**
 * Test suite for asset enrichment functionality to ensure proper asset data enhancement.
 *
 * This class tests:
 * - FIGI enrichment for assets without names
 * - Alpha Vantage enrichment for market data
 * - Private market asset enrichment for custom assets
 * - Enrichment condition validation (when enrichment should/shouldn't occur)
 * - Asset code generation and system user assignment
 *
 * Tests verify that different enrichment strategies work correctly
 * and only enrich assets when appropriate conditions are met.
 */
class EnrichmentTest {
    // Test constants
    private val testAssetCode = "code"
    private val testAssetName = "test"
    private val testAssetId = "123"
    private val testKeyGenId = "UniqueId"
    private val testSystemUserId = "sysUserId"

    private val accountingTypeService = Mockito.mock(AccountingTypeService::class.java)
    private val currencyService = Mockito.mock(CurrencyService::class.java)

    @Test
    fun `should enrich asset with FIGI data when asset name is null or blank`() {
        // Given a FIGI enricher and an asset without a name
        val enricher: AssetEnricher = FigiEnricher(DefaultEnricher(accountingTypeService, currencyService))
        val asset =
            Asset(
                code = testAssetCode,
                id = testAssetId,
                name = null,
                market = NYSE
            )

        // When checking if the asset can be enriched
        assertThat(enricher.canEnrich(asset)).isTrue()

        // And when the name is empty string
        asset.name = ""
        assertThat(enricher.canEnrich(asset)).isTrue()

        // And when the name is blank
        asset.name = "  "
        assertThat(enricher.canEnrich(asset)).isTrue()

        // When the asset name is set to a real value
        asset.name = testAssetName

        // Then enrichment should no longer be possible
        assertThat(enricher.canEnrich(asset)).isFalse()
    }

    @Test
    fun `should enrich asset with Alpha Vantage data when asset name is null`() {
        // Given an Alpha Vantage enricher and an asset without a name
        val dateUtils = DateUtils()
        val alphaProxy = Mockito.mock(AlphaProxy::class.java)
        val enricher: AssetEnricher =
            AlphaEnricher(
                AlphaConfig(
                    dateUtils = dateUtils,
                    PreviousClosePriceDate(dateUtils)
                ),
                DefaultEnricher(accountingTypeService, currencyService),
                alphaProxy,
                accountingTypeService,
                currencyService
            )
        val asset =
            Asset(
                code = testAssetCode,
                id = testAssetId,
                name = null,
                market = NYSE
            )

        // When checking if the asset can be enriched
        val canEnrich = enricher.canEnrich(asset)

        // Then enrichment should be possible
        assertThat(canEnrich).isTrue()

        // When the asset name is set
        asset.name = testAssetName

        // Then enrichment should no longer be possible
        assertThat(enricher.canEnrich(asset)).isFalse()
    }

    @Test
    fun `should enrich private market asset with system user and custom code when asset name is null`() {
        // Given a private market enricher and an asset without a name
        val privateMarket = Market(PrivateMarketDataProvider.ID)
        val keyGenUtils = Mockito.mock(KeyGenUtils::class.java)
        val systemUserService = Mockito.mock(SystemUserService::class.java)
        val enricher: AssetEnricher = PrivateMarketEnricher(systemUserService, accountingTypeService, currencyService)

        // And mocked dependencies
        Mockito.`when`(systemUserService.getOrThrow()).thenReturn(SystemUser(testSystemUserId))
        Mockito.`when`(keyGenUtils.id).thenReturn(testKeyGenId)
        Mockito.`when`(currencyService.getCode("USD")).thenReturn(USD)
        Mockito
            .`when`(accountingTypeService.getOrCreate(any(), any(), any(), any()))
            .thenReturn(AccountingType(id = "test", category = "RE", currency = USD))

        val asset =
            Asset(
                code = testAssetCode,
                id = testAssetId,
                name = null,
                market = privateMarket
            )

        // When checking if the asset can be enriched
        val canEnrich = enricher.canEnrich(asset)

        // Then enrichment should be possible
        assertThat(canEnrich).isTrue()

        // When the asset is enriched
        val enriched =
            enricher.enrich(
                asset.id,
                privateMarket,
                AssetInput.toRealEstate(
                    USD,
                    testAssetCode,
                    "Anything",
                    "test-user"
                )
            )

        // Then the enriched asset should have the correct system user and code
        assertThat(enriched)
            .hasFieldOrPropertyWithValue("systemUser.id", testSystemUserId)
            .hasFieldOrPropertyWithValue("code", "$testSystemUserId.${testAssetCode.uppercase()}")
    }

    @Test
    fun `should fall through to next enricher in chain when primary returns no name`() {
        // Given a primary enricher that returns an asset with no name
        val primaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = null,
                        market = market,
                        marketCode = market.code
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "PRIMARY"
            }

        // And a secondary enricher that provides a name
        val expectedName = "DBS GROUP HOLDINGS"
        val secondaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = expectedName,
                        market = market,
                        marketCode = market.code
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "SECONDARY"
            }

        // When chained together
        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val chainedEnricher = ChainedEnricher(listOf(primaryEnricher, secondaryEnricher), defaultEnricher)
        val sgx = Market("SGX", currencyId = "SGD")
        val assetInput = AssetInput("SGX", "D05")

        val result = chainedEnricher.enrich(testAssetId, sgx, assetInput)

        // Then the secondary enricher's name should be used
        assertThat(result.name).isEqualTo(expectedName)
    }

    @Test
    fun `should merge priceSymbol from secondary enricher when primary provides name`() {
        // Given a primary enricher (FIGI) that provides name but sets priceSymbol = code
        val expectedName = "DBS GROUP HOLDINGS LTD"
        val primaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = expectedName,
                        market = market,
                        marketCode = market.code,
                        priceSymbol = assetInput.code.uppercase(Locale.getDefault())
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "FIGI"
            }

        // And a secondary enricher (MarketStack) that provides the correct priceSymbol
        val expectedPriceSymbol = "D05.SI"
        val secondaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = "DBS Group Holdings Ltd",
                        market = market,
                        marketCode = market.code,
                        priceSymbol = expectedPriceSymbol
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "MSTACK"
            }

        // When chained together
        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val chainedEnricher = ChainedEnricher(listOf(primaryEnricher, secondaryEnricher), defaultEnricher)
        val sgx = Market("SGX", currencyId = "SGD")
        val assetInput = AssetInput("SGX", "DBS")

        val result = chainedEnricher.enrich(testAssetId, sgx, assetInput)

        // Then name from FIGI, priceSymbol from MarketStack
        assertThat(result.name).isEqualTo(expectedName)
        assertThat(result.priceSymbol).isEqualTo(expectedPriceSymbol)
    }

    @Test
    fun `should keep priceSymbol from primary when it differs from code`() {
        // Given a primary enricher that provides a real priceSymbol (not just code)
        val primaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = "SPARK NEW ZEALAND LTD",
                        market = market,
                        marketCode = market.code,
                        priceSymbol = "SPK.NZ"
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "FIGI"
            }

        // And a secondary enricher with a different priceSymbol
        val secondaryEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = "Spark New Zealand Limited",
                        market = market,
                        marketCode = market.code,
                        priceSymbol = "SPK.NZ"
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "MSTACK"
            }

        // When chained together
        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val chainedEnricher = ChainedEnricher(listOf(primaryEnricher, secondaryEnricher), defaultEnricher)
        val nzx = Market("NZX", currencyId = "NZD")
        val assetInput = AssetInput("NZX", "SPK")

        val result = chainedEnricher.enrich(testAssetId, nzx, assetInput)

        // Then primary's priceSymbol is kept (it already differs from code)
        assertThat(result.name).isEqualTo("SPARK NEW ZEALAND LTD")
        assertThat(result.priceSymbol).isEqualTo("SPK.NZ")
    }

    @Test
    fun `should fall back to default enricher when all chain enrichers fail`() {
        // Given enrichers that all return null names
        val failingEnricher =
            object : AssetEnricher {
                override fun enrich(
                    id: String,
                    market: Market,
                    assetInput: AssetInput
                ): Asset =
                    Asset(
                        code = assetInput.code.uppercase(Locale.getDefault()),
                        id = id,
                        name = null,
                        market = market,
                        marketCode = market.code
                    )

                override fun canEnrich(asset: Asset): Boolean = asset.name == null

                override fun id(): String = "FAILING"
            }

        Mockito.`when`(currencyService.getCode("SGD")).thenReturn(USD)
        Mockito
            .`when`(accountingTypeService.getOrCreate(any(), any(), any(), any()))
            .thenReturn(AccountingType(id = "test", category = "Equity", currency = USD))

        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val chainedEnricher = ChainedEnricher(listOf(failingEnricher), defaultEnricher)
        val sgx = Market("SGX", currencyId = "SGD")
        val assetInput = AssetInput("SGX", "UNKNOWN")

        val result = chainedEnricher.enrich(testAssetId, sgx, assetInput)

        // Then the default enricher should have set name to null (no external name available)
        assertThat(result.code).isEqualTo("UNKNOWN")
        assertThat(result.market.code).isEqualTo("SGX")
    }

    @Test
    fun `should parse comma-separated enricher config into chain`() {
        // Given an EnrichmentFactory with registered enrichers
        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val factory = EnrichmentFactory(defaultEnricher)

        val mockFigi = Mockito.mock(AssetEnricher::class.java)
        Mockito.`when`(mockFigi.id()).thenReturn("FIGI")
        val mockMstack = Mockito.mock(AssetEnricher::class.java)
        Mockito.`when`(mockMstack.id()).thenReturn("MSTACK")

        factory.register(mockFigi)
        factory.register(mockMstack)

        // When a market has a comma-separated enricher config
        val sgx = Market("SGX", enricher = "FIGI,MSTACK")

        val enricher = factory.getEnricher(sgx)

        // Then it should return a ChainedEnricher
        assertThat(enricher).isInstanceOf(ChainedEnricher::class.java)
        assertThat(enricher.id()).isEqualTo("FIGI,MSTACK")
    }

    @Test
    fun `should return single enricher for non-chained config`() {
        // Given an EnrichmentFactory with registered enrichers
        val defaultEnricher = DefaultEnricher(accountingTypeService, currencyService)
        val factory = EnrichmentFactory(defaultEnricher)

        val mockFigi = Mockito.mock(AssetEnricher::class.java)
        Mockito.`when`(mockFigi.id()).thenReturn("FIGI")
        factory.register(mockFigi)

        // When a market has a single enricher
        val nyse = Market("NYSE", enricher = "FIGI")

        val enricher = factory.getEnricher(nyse)

        // Then it should return the enricher directly (not wrapped in a chain)
        assertThat(enricher).isSameAs(mockFigi)
    }
}