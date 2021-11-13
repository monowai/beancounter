package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

/**
 * Are assets hydrated correctly?
 */
@SpringBootTest(
    classes = [
        AssetHydrationService::class,
        AssetCategoryConfig::class,
    ]
)
internal class AssetHydrationServiceTest {
    @Autowired
    private lateinit var assetHydrationService: AssetHydrationService

    @MockBean
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @BeforeEach
    private fun mockData() {
        Mockito.`when`(marketService.getMarket(NYSE.code)).thenReturn(NYSE)
    }

    @Test
    fun hydrateEquityWithDefaults() {
        val assetInput = AssetInput(NYSE.code, "EQUITY", category = "Equity")
        val hydratedAsset = assetHydrationService.hydrateAsset(Asset(assetInput, NYSE))
        validate(hydratedAsset, assetCategoryConfig.get())
    }

    @Test
    fun hydrateMutualFund() {
        val assetInput = AssetInput(NYSE.code, "Fund", category = "Mutual Fund")
        val hydratedAsset = assetHydrationService.hydrateAsset(Asset(assetInput, NYSE))
        validate(hydratedAsset, assetCategoryConfig.get("Mutual Fund"))
    }

    @Test
    fun hydrateCash() {
        val assetInput = AssetInput(NYSE.code, "USD Cash", category = "Cash")
        val hydratedAsset = assetHydrationService.hydrateAsset(Asset(assetInput, NYSE))
        validate(hydratedAsset, assetCategoryConfig.get("Cash"))
    }

    private fun validate(hydratedAsset: Asset, category: AssetCategory?) {
        assertThat(hydratedAsset)
            .hasFieldOrPropertyWithValue("market", NYSE)
            .hasFieldOrPropertyWithValue("assetCategory", category)
    }
}
