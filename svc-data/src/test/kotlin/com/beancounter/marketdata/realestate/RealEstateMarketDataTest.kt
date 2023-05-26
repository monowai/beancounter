package com.beancounter.marketdata.realestate

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.server.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.Optional

private const val OFF_MARKET = "OFFM"

@SpringBootTest
@ActiveProfiles("test")
class RealEstateMarketDataTest {

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
    @MockBean
    lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    lateinit var marketDataService: MarketDataService

    val reInput = AssetInput.toRealEstate(
        currency = NZD,
        code = "PNZ",
        name = "My Place In NZD",
        owner = "test-user",
    )

    @Test
    fun offMarketProviderExists() {
        val provider = mdFactory.getMarketDataProvider(Market(OFF_MARKET))
        assertThat(provider).isNotNull
    }

    @Test
    fun mockPriceResponseFlow() {
        mockAuthConfig.login(SystemUser(id = "test-user"), this.systemUserService)

        val assetResponse = assetService.handle(
            AssetRequest(
                mapOf(Pair(NZD.code, reInput)),
            ),
        )
        val asset = assetResponse.data.iterator().next().value

        Mockito.`when`(
            marketDataRepo.findByAssetIdAndPriceDate(asset.id, DateUtils().date),
        ).thenReturn(Optional.of(MarketData(asset, close = BigDecimal.TEN)))
        val prices = marketDataService.getPriceResponse(
            priceRequest = PriceRequest(
                assets = listOf(
                    PriceAsset(asset),
                ),
            ),
        )

        assertThat(prices.data).hasSize(1)
        assertThat(prices.data.iterator().next())
            .hasFieldOrPropertyWithValue("close", BigDecimal.TEN)
    }
}
