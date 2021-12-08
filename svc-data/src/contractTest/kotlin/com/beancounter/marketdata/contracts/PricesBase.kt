package com.beancounter.marketdata.contracts

import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.wtd.WtdGateway
import com.beancounter.marketdata.providers.wtd.WtdMarketData
import com.beancounter.marketdata.providers.wtd.WtdResponse
import contracts.ContractHelper
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

/**
 * Base class for Price Contract tests. This is called by the spring cloud contract verifier
 */
class PricesBase : ContractVerifierBase() {

    @MockBean
    internal lateinit var wtdGateway: WtdGateway

    @MockBean
    private lateinit var assetService: AssetService

    private lateinit var systemUser: SystemUser

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        systemUser = ContractHelper.defaultUser(
            jwtDecoder = jwtDecoder,
            tokenService = tokenService,
            systemUserRepository = systemUserRepository
        )
        AssetsBase().mockAssets(assetService)
        mockPrices()
    }

    fun mockPrices() {
        val marketData = WtdMarketData(
            BigDecimal("39.21"),
            BigDecimal("100.00"),
            BigDecimal("38.74"),
            BigDecimal("39.35"),
            Integer.decode("6274307")
        )

        mockPriceResponse("EBAY", marketData)
        mockPriceResponse("MSFT", marketData)
        mockPriceResponse("AAPL", marketData)
        // Cash Ladder Prices
        mockPriceResponse("AAPL", marketData, "2021-10-18")
    }

    private fun mockPriceResponse(code: String, marketData: WtdMarketData, date: String = rateDate) {
        val result: MutableMap<String, WtdMarketData> = HashMap()
        result[code] = marketData
        val priceResponse = WtdResponse(date, result)
        Mockito.`when`(
            wtdGateway
                .getPrices(code, date, "demo")
        ).thenReturn(priceResponse)
    }
}
