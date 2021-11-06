package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.assetProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.closeProp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
class AlphaApiInfraTest {
    private val api = "API"

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var marketService: MarketService

    @Test
    @Throws(Exception::class)
    fun is_ApiInvalidKeyHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageInfo.json").file
        AlphaMockUtils.mockGlobalResponse("$api.KEY", jsonFile)
        val asset = Asset(api, Market("KEY", Constants.USD))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID)
        val results = alphaProvider.getMarketData(
            PriceRequest.Companion.of(asset)
        )
        Assertions.assertThat(results)
            .isNotNull
            .hasSize(1)
        Assertions.assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiCallLimitExceededHandled() {
        val nasdaq = marketService.getMarket(Constants.NASDAQ.code)
        val asset = Asset("ABC", nasdaq)
        asset.id = asset.code
        AlphaMockUtils.mockGlobalResponse(
            asset.id,
            ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageNote.json").file
        )
        Assertions.assertThat(asset).isNotNull
        val assetInput = AssetInput(asset)

        val results = mdFactory.getMarketDataProvider(AlphaService.ID)
            .getMarketData(
                PriceRequest.of(assetInput)
            )
        Assertions.assertThat(results)
            .isNotNull
            .hasSize(1)
        val mdpPrice = results.iterator().next()
        Assertions.assertThat(mdpPrice)
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
        val priceResponse = marketDataService.getPriceResponse(PriceRequest.of(assetInput))
        Assertions.assertThat(priceResponse).isNotNull
    }
}
