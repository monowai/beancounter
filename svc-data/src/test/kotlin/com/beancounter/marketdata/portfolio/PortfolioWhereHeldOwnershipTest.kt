package com.beancounter.marketdata.portfolio

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.PORTFOLIO_ROOT
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.beancounter.marketdata.utils.TRADE_DATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Verifies that findWhereHeld only returns portfolios the caller can view
 * (portfolios they own, or that are shared with them).
 */
@SpringMvcDbTest
class PortfolioWhereHeldOwnershipTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dateUtils: DateUtils,
    @Autowired private val mockAuthConfig: MockAuthConfig
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    private lateinit var userAHelper: BcMvcHelper
    private lateinit var userBHelper: BcMvcHelper
    private lateinit var msftId: String

    @BeforeEach
    fun setup() {
        val tokenA =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser(id = "userA", email = "a@monowai.com", auth0 = "userA"))
            )
        val tokenB =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(SystemUser(id = "userB", email = "b@monowai.com", auth0 = "userB"))
            )
        userAHelper = BcMvcHelper(mockMvc, tokenA)
        userBHelper = BcMvcHelper(mockMvc, tokenB)

        msftId =
            userAHelper
                .asset(AssetRequest(AssetUtils.getAssetInput(NASDAQ.code, "MSFT.whereheld")))
                .id

        val portfolioA = userAHelper.portfolio(PortfolioInput("WHA", "WHA", currency = Constants.NZD.code))
        val portfolioB = userBHelper.portfolio(PortfolioInput("WHB", "WHB", currency = Constants.NZD.code))

        userAHelper.postTrn(TrnRequest(portfolioA.id, listOf(trnInput("a1"))))
        userBHelper.postTrn(TrnRequest(portfolioB.id, listOf(trnInput("b1"))))
    }

    private fun trnInput(callerId: String) =
        TrnInput(
            CallerRef(batch = "0", callerId = callerId),
            msftId,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getFormattedDate(TRADE_DATE),
            price = BigDecimal.TEN,
            status = TrnStatus.SETTLED
        )

    @Test
    fun whereHeldFiltersToCallersPortfolios() {
        val asUserA =
            mockMvc
                .perform(
                    get("$PORTFOLIO_ROOT/asset/{assetId}", msftId)
                        .param("asAt", TRADE_DATE)
                        .with(jwt().jwt(userAHelper.token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val portfoliosForA =
            objectMapper.readValue(asUserA.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfoliosForA.data).hasSize(1)
        assertThat(portfoliosForA.data.first().code).isEqualTo("WHA")

        val asUserB =
            mockMvc
                .perform(
                    get("$PORTFOLIO_ROOT/asset/{assetId}", msftId)
                        .param("asAt", TRADE_DATE)
                        .with(jwt().jwt(userBHelper.token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        val portfoliosForB =
            objectMapper.readValue(asUserB.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfoliosForB.data).hasSize(1)
        assertThat(portfoliosForB.data.first().code).isEqualTo("WHB")
    }
}