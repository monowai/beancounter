package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.assetProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.closeProp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
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
class AlphaApiInfraTest {
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
        Mockito.`when`(dateUtils.isToday(anyString())).thenReturn(true)
    }

    @Test
    fun is_ApiInvalidKeyHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageInfo.json").file
        AlphaMockUtils.mockGlobalResponse("$api.KEY", jsonFile)
        val asset = getTestAsset(code = api, market = Market("KEY", Constants.USD.code))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaPriceService.ID)
        val results = alphaProvider.getMarketData(
            PriceRequest.Companion.of(asset),
        )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
    }

    @Test
    fun is_ApiCallLimitExceededHandled() {
        val nasdaq = marketService.getMarket(Constants.NASDAQ.code)
        val asset = getTestAsset(code = "ABC", market = nasdaq)
        AlphaMockUtils.mockGlobalResponse(
            asset.id,
            ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageNote.json").file,
        )
        assertThat(asset).isNotNull

        val results = mdFactory.getMarketDataProvider(AlphaPriceService.ID)
            .getMarketData(
                PriceRequest.of(asset),
            )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        val mdpPrice = results.iterator().next()
        assertThat(mdpPrice)
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
        val priceResponse = marketDataService.getPriceResponse(PriceRequest.of(asset))
        assertThat(priceResponse).isNotNull
    }
}
