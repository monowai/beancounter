package com.beancounter.marketdata.trn

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
import com.beancounter.marketdata.portfolio.PortfolioService
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
 * Transactional range query tests - verifies finding portfolios that hold an asset.
 */
@SpringMvcDbTest
class TrnPortfolioControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val dateUtils: DateUtils,
    @Autowired private val portfolioService: PortfolioService,
    @Autowired private val mockAuthConfig: MockAuthConfig
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var msftId: String

    @BeforeEach
    fun setup() {
        val token = mockAuthConfig.getUserToken(SystemUser(auth0 = "auth0"))
        bcMvcHelper = BcMvcHelper(mockMvc, token)
        registerUser(mockMvc, token)

        msftId =
            bcMvcHelper
                .asset(
                    AssetRequest(
                        AssetUtils.getAssetInput(
                            NASDAQ.code,
                            "MSFT.code"
                        )
                    )
                ).id

        // Create two portfolios with transactions at different dates
        val portfolioA = bcMvcHelper.portfolio(PortfolioInput("PCA", "PCA-NAME", currency = Constants.NZD.code))
        val portfolioB = bcMvcHelper.portfolio(PortfolioInput("PCB", "PCB-NAME", currency = Constants.NZD.code))

        bcMvcHelper.postTrn(
            TrnRequest(
                portfolioA.id,
                listOf(
                    trnInput("1", "2016-01-01"),
                    trnInput("2", TRADE_DATE)
                )
            )
        )
        bcMvcHelper.postTrn(
            TrnRequest(
                portfolioB.id,
                listOf(
                    trnInput("3", "2018-10-01"),
                    trnInput("4", "2017-01-01")
                )
            )
        )
    }

    private fun trnInput(
        callerId: String,
        tradeDate: String
    ) = TrnInput(
        CallerRef(batch = "0", callerId = callerId),
        msftId,
        trnType = TrnType.BUY,
        quantity = BigDecimal.TEN,
        tradeDate = dateUtils.getFormattedDate(tradeDate),
        price = BigDecimal.TEN,
        status = TrnStatus.SETTLED
    )

    @Test
    fun is_TrnForPortfolioInRangeFound() {
        // Find portfolios holding MSFT as of TRADE_DATE via REST endpoint
        val response =
            mockMvc
                .perform(
                    get("$PORTFOLIO_ROOT/asset/{assetId}/{tradeDate}", msftId, TRADE_DATE)
                        .with(jwt().jwt(bcMvcHelper.token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        val portfolios = objectMapper.readValue(response.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfolios.data).hasSize(2)

        // Find portfolios as of early date (only one portfolio had transactions by then)
        assertThat(portfolioService.findWhereHeld(msftId, dateUtils.getFormattedDate("2016-01-01")).data).hasSize(1)

        // Find all portfolios holding the asset (no date filter)
        assertThat(portfolioService.findWhereHeld(msftId, null).data).hasSize(2)
    }
}