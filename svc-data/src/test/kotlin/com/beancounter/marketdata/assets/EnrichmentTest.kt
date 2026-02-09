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
    fun `should enrich asset with FIGI data when asset name is null`() {
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
        val canEnrich = enricher.canEnrich(asset)

        // Then enrichment should be possible
        assertThat(canEnrich).isTrue()

        // When the asset name is set
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
}