package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Verify price refresh removes and re-imports the price for a single asset.
 */
@SpringMvcDbTest
internal class PriceRefreshTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @Autowired
    private lateinit var priceRefresh: PriceRefresh

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @BeforeEach
    fun mockAlpha() {
        Mockito.`when`(alphaPriceService.getId()).thenReturn(AlphaPriceService.ID)
    }

    @Test
    fun updatePrices() {
        val keyGenUtils = KeyGenUtils()
        val code = keyGenUtils.id
        Mockito
            .`when`(
                alphaPriceService.getMarketData(
                    PriceRequest(
                        TODAY,
                        listOf(
                            PriceAsset(
                                NASDAQ.code,
                                code = code
                            )
                        )
                    )
                )
            ).thenReturn(
                listOf(
                    MarketData(
                        Asset(
                            code = "",
                            market = NASDAQ
                        )
                    )
                )
            )
        val asset =
            assetRepository.save(
                Asset(
                    code = code,
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val hydratedAsset = assetFinder.hydrateAsset(asset)
        assertThat(hydratedAsset).hasFieldOrProperty("market")
        val count = priceRefresh.updatePrices()
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `findActiveIndexAssets returns INDEX market assets without holdings`() {
        val indexCode = "^GSPC_TEST"
        val indexMarket = Market("INDEX")
        assetRepository.save(
            Asset(
                code = indexCode,
                id = indexCode,
                market = indexMarket,
                marketCode = indexMarket.code,
                category = AssetCategory.INDEX
            )
        )

        val indices = assetFinder.findActiveIndexAssets()
        assertThat(indices.map { it.code }).contains(indexCode)
    }

    @Test
    fun `updatePrices includes index assets even without holdings`() {
        val indexCode = "^GSPC_REFRESH"
        val indexMarket = Market("INDEX")
        Mockito
            .`when`(alphaPriceService.isMarketSupported(org.mockito.kotlin.any()))
            .thenReturn(true)
        assetRepository.save(
            Asset(
                code = indexCode,
                id = indexCode,
                market = indexMarket,
                marketCode = indexMarket.code,
                category = AssetCategory.INDEX
            )
        )

        priceRefresh.updatePrices()

        // Confirms PriceRefresh enrolled the index asset in its refresh loop:
        // routing through MdFactory.resolveProvider → isMarketSupported is the
        // first observable signal that the asset entered the price pipeline.
        val captor = org.mockito.kotlin.argumentCaptor<Market>()
        Mockito.verify(alphaPriceService, Mockito.atLeastOnce()).isMarketSupported(captor.capture())
        assertThat(captor.allValues.map { it.code }).contains("INDEX")
    }
}