package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.config.StaticConfigService
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Are assets hydrated correctly?
 */
@SpringMvcDbTest
@AutoConfigureMockAuth
class AssetFinderTest {
    @Autowired
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var staticConfigService: StaticConfigService

    @MockitoBean
    private lateinit var marketService: MarketService

    @MockitoBean
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @BeforeEach
    fun mockData() {
        val cashMarket = Market(code = "CASH")
        Mockito.`when`(marketService.getMarket(NYSE.code)).thenReturn(NYSE)
        Mockito.`when`(marketService.getMarket("CASH")).thenReturn(cashMarket)
        Mockito.`when`(marketService.getMarket(Mockito.anyString())).thenReturn(NYSE)
    }

    @Test
    fun `should hydrate equity with defaults`() {
        val assetInput =
            AssetInput(
                NYSE.code,
                "EQUITY",
                category = "Equity"
            )
        val hydratedAsset =
            assetFinder.hydrateAsset(
                Asset.of(
                    assetInput,
                    NYSE
                )
            )
        validate(
            hydratedAsset,
            assetCategoryConfig.get()
        )
    }

    @Test
    fun `should hydrate mutual fund`() {
        val assetInput =
            AssetInput(
                NYSE.code,
                "Fund",
                category = "Mutual Fund"
            )
        val hydratedAsset =
            assetFinder.hydrateAsset(
                Asset.of(
                    assetInput,
                    NYSE
                )
            )
        validate(
            hydratedAsset,
            assetCategoryConfig.get("Mutual Fund")
        )
    }

    @Test
    fun `should hydrate cash`() {
        val assetInput =
            AssetInput(
                NYSE.code,
                "USD Cash",
                category = "Cash"
            )
        val hydratedAsset =
            assetFinder.hydrateAsset(
                Asset.of(
                    assetInput,
                    NYSE
                )
            )
        validate(
            hydratedAsset,
            assetCategoryConfig.get("Cash")
        )
    }

    @Test
    fun `should handle unknown asset category gracefully`() {
        val assetInput =
            AssetInput(
                NYSE.code,
                "UNKNOWN",
                category = "UnknownCategory"
            )
        val asset = Asset.of(assetInput, NYSE)

        // This should not throw NPE - should fall back to default category
        val hydratedAsset = assetFinder.hydrateAsset(asset)

        assertThat(hydratedAsset.assetCategory)
            .isEqualTo(assetCategoryConfig.get()) // Should be default category
    }

    private fun validate(
        hydratedAsset: Asset,
        category: AssetCategory?
    ) {
        assertThat(hydratedAsset)
            .hasFieldOrPropertyWithValue(
                "market",
                NYSE
            ).hasFieldOrPropertyWithValue(
                "assetCategory",
                category
            )
    }
}