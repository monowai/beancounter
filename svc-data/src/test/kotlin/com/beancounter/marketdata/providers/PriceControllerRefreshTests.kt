package com.beancounter.marketdata.providers

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.US
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * MVC tests related to market activities.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class PriceControllerRefreshTests
@Autowired private constructor(
    private val dateUtils: DateUtils,
    private val mockMvc: MockMvc,
    private val mockAuthConfig: MockAuthConfig,
) {
    @MockBean
    private lateinit var alphaPriceService: AlphaPriceService

    @MockBean
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var mdFactory: MdFactory
    private var testDate = dateUtils.getLocalDate()

    private val asset = Asset(code = "DUMMY", market = US)
    private val priceRequest = PriceRequest(
        date = testDate.toString(),
        assets = listOf(PriceAsset(asset)),
    )

    @BeforeEach
    fun mockAlphaPriceService() {
        Mockito.`when`(assetService.find(asset.id)).thenReturn(asset)
        Mockito.`when`(alphaPriceService.isMarketSupported(US))
            .thenReturn(true)

        Mockito.`when`(alphaPriceService.getId())
            .thenReturn(AlphaPriceService.ID)

        assertThat(mdFactory.getMarketDataProvider(US).getId())
            .isEqualTo(AlphaPriceService.ID)
        Mockito.`when`(alphaPriceService.getDate(asset.market, priceRequest))
            .thenReturn(testDate)
//        Mockito.`when`(alphaPriceService.getDate(asset.market, priceRequest))
//            .thenReturn(date)
    }

    @Test
    @WithMockUser(username = "test-user", roles = [AuthConstants.USER])
    fun is_refreshServiceTestWorking() {
        // Start price setup
        Mockito.`when`(alphaPriceService.getMarketData(priceRequest = priceRequest))
            .thenReturn(listOf(MarketData(asset = asset, close = BigDecimal("10.00"))))

        val response = marketDataService.getPriceResponse(priceRequest)
        assertThat(response.data)
            .isNotNull.hasSize(1)

        assertThat(response.data.iterator().next().close)
            .isEqualTo(BigDecimal("10.00"))
        // End setup

        // TEST perform refresh. Make sure the price we get is set correctly to 11 and not 10
        Mockito.`when`(alphaPriceService.getMarketData(priceRequest = priceRequest))
            .thenReturn(listOf(MarketData(asset = asset, close = BigDecimal("11.00"))))
        mockMvc.perform(
            MockMvcRequestBuilders.get("/prices/refresh/{assetId}/{date}", asset.id, testDate)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                .contentType(MediaType.APPLICATION_JSON_VALUE),
        )
            .andExpect(
                MockMvcResultMatchers.status().isOk,
            )

        assertThat(marketDataService.getPriceResponse(priceRequest).data.iterator().next().close)
            .isEqualTo(BigDecimal("11.00"))
    }
}