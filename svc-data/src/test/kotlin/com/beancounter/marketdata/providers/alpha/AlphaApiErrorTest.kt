package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_ASSET
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_CLOSE
import com.beancounter.marketdata.utils.DateUtilsMocker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

/**
 * AlphaApi sanity tests
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("alpha")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
class AlphaApiErrorTest {
    private val api = "API"

    @Autowired
    private lateinit var mdFactory: MdFactory

    @MockBean
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var marketService: MarketService

    @BeforeEach
    fun mock() {
        DateUtilsMocker.mockToday(dateUtils)
    }

    @Test
    fun is_ApiErrorMessageHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.ALPHA_MOCK + "/alphavantageError.json").file
        AlphaMockUtils.mockGlobalResponse("${AlphaConstants.API}.ERR", jsonFile)
        val asset = Asset(code = AlphaConstants.API, market = Market("ERR", Constants.USD.code))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaPriceService.ID)

        val results = alphaProvider.getMarketData(PriceRequest.of(asset))
        assertThat(results)
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(P_ASSET, asset)
            .hasFieldOrPropertyWithValue(P_CLOSE, BigDecimal.ZERO)
    }

    @Test
    fun is_ApiInvalidKeyHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.ALPHA_MOCK + "/alphavantageInfo.json").file
        AlphaMockUtils.mockGlobalResponse("$api.KEY", jsonFile)
        val asset = getTestAsset(code = api, market = Market("KEY", Constants.USD.code))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaPriceService.ID)

        val results =
            alphaProvider.getMarketData(
                PriceRequest.Companion.of(asset),
            )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(P_ASSET, asset)
            .hasFieldOrPropertyWithValue(P_CLOSE, BigDecimal.ZERO)
    }

    @Test
    fun is_ApiCallLimitExceededHandled() {
        val nasdaq = marketService.getMarket(Constants.NASDAQ.code)
        val asset = getTestAsset(code = "ABC", market = nasdaq)
        AlphaMockUtils.mockGlobalResponse(
            asset.id,
            ClassPathResource(AlphaMockUtils.ALPHA_MOCK + "/alphavantageNote.json").file,
        )
        assertThat(asset).isNotNull

        val results =
            mdFactory.getMarketDataProvider(AlphaPriceService.ID)
                .getMarketData(
                    PriceRequest.of(asset),
                )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        val mdpPrice = results.iterator().next()
        assertThat(mdpPrice)
            .hasFieldOrPropertyWithValue(P_ASSET, asset)
            .hasFieldOrPropertyWithValue(P_CLOSE, BigDecimal.ZERO)
        assertThat(marketDataService.getPriceResponse(PriceRequest.of(asset))).isNotNull
    }
}
