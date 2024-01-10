package com.beancounter.marketdata.providers

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.assets.AssetHydrationService
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

/**
 * Verify price refresh removes and re-imports the price for a single asset.
 */
@SpringBootTest
@Tag("slow")
@Tag("db")
@ActiveProfiles("test")
@AutoConfigureMockAuth
internal class PriceRefreshTest {
    @Autowired
    private lateinit var priceRefresh: PriceRefresh

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetHydrationService: AssetHydrationService

    @Autowired
    private lateinit var marketService: MarketService

    @MockBean
    private lateinit var alphaPriceService: AlphaPriceService

    @BeforeEach
    fun mockAlpha() {
        Mockito.`when`(alphaPriceService.getId()).thenReturn(AlphaPriceService.ID)
    }

    @Test
    fun updatePrices() {
        val keyGenUtils = KeyGenUtils()
        val code = keyGenUtils.id
        Mockito.`when`(
            alphaPriceService.getMarketData(
                PriceRequest(TODAY, listOf(PriceAsset(NASDAQ.code, code = code))),
            ),
        ).thenReturn(listOf(MarketData(Asset(code = "", market = NASDAQ))))
        val asset =
            assetRepository.save(
                Asset(
                    code = code,
                    market = NASDAQ,
                    marketCode = NASDAQ.code,
                ),
            )
        val hydratedAsset = assetHydrationService.hydrateAsset(asset)
        assertThat(hydratedAsset).hasFieldOrProperty("market")
        val completable = priceRefresh.updatePrices()
        assertThat(completable.get()).isGreaterThanOrEqualTo(1)
    }
}
