package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

/**
 * Validate prices for off market assets.
 */
@SpringBootTest
@ActiveProfiles("test")
class OffMarketPriceDataTest {
    @Autowired
    lateinit var marketService: MarketService

    @Autowired
    lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    lateinit var marketDataService: MarketDataService

    @Autowired
    lateinit var priceService: PriceService

    private val owner = "test-user"

    @BeforeEach
    fun init() {
        mockAuthConfig.login(SystemUser(id = owner), this.systemUserService)
    }

    @Test
    fun offMarketProviderExists() {
        val provider = mdFactory.getMarketDataProvider(Market(OffMarketDataProvider.ID))
        assertThat(provider).isNotNull
    }

    @Test
    fun findNoPrice() {
        val assetResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            NZD.code,
                            AssetInput.toRealEstate(
                                currency = NZD,
                                code = "NO-PRICE",
                                name = "Worthless place",
                                owner = owner,
                            ),
                        ),
                    ),
                ),
            )
        val asset = assetResponse.data.iterator().next().value

        // No price exists, so return 0
        val prices =
            marketDataService.getPriceResponse(
                priceRequest =
                    PriceRequest(
                        assets =
                            listOf(
                                PriceAsset(asset),
                            ),
                    ),
            )

        assertThat(prices.data).hasSize(1)
        assertThat(prices.data.iterator().next())
            .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
    }

    @Test
    fun findClosestPrice() {
        val assetResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(
                        Pair(
                            NZD.code,
                            AssetInput.toRealEstate(
                                currency = NZD,
                                code = "PNZ",
                                name = "My Place In NZD",
                                owner = owner,
                            ),
                        ),
                    ),
                ),
            )
        val asset = assetResponse.data.iterator().next().value

        val priceResponse =
            PriceResponse(
                listOf(
                    priceService.getMarketData(
                        asset = asset,
                        date = DateUtils().getDate("2022-01-01"),
                        closePrice = BigDecimal.TEN,
                    ).get(),
                ),
            )

        assertThat(priceResponse.data).hasSize(1)

        // Should return the last known price
        val prices =
            marketDataService.getPriceResponse(
                priceRequest =
                    PriceRequest(
                        assets =
                            listOf(
                                PriceAsset(asset),
                            ),
                    ),
            )

        assertThat(prices.data).hasSize(1)
        assertThat(prices.data.iterator().next())
            .hasFieldOrPropertyWithValue("close", BigDecimal("10.000000"))
            .hasFieldOrPropertyWithValue("asset.market.code", OffMarketDataProvider.ID)
    }
}
