package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Transactional range query tests.
 */
@SpringMvcDbTest
class TrnPortfolioControllerTest {
    private lateinit var token: Jwt

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockBean
    private lateinit var fxTransactions: FxTransactions

    private lateinit var bcMvcHelper: BcMvcHelper

    private lateinit var msft: Asset

    @Autowired
    fun setupTestEnvironment(mockAuthConfig: MockAuthConfig) {
        initializeAuthentication(mockAuthConfig)
        registerUser(mockMvc, token)
        setupAssetsAndTransactions()
    }

    private fun initializeAuthentication(mockAuthConfig: MockAuthConfig) {
        token = mockAuthConfig.getUserToken(SystemUser(auth0 = "auth0"))
        bcMvcHelper = BcMvcHelper(mockMvc, token)
    }

    private fun setupAssetsAndTransactions() {
        msft = bcMvcHelper.asset(AssetRequest(Constants.msftInput))
        postTransactions()
    }

    private fun postTransactions() {
        val portfolioA = createPortfolio("PCA", "PCA-NAME")
        val portfolioB = createPortfolio("PCB", "PCB-NAME")

        val transactionsA =
            arrayOf(
                createTransactionInput("1", "2016-01-01", BigDecimal.ONE),
                createTransactionInput("2", BcMvcHelper.TRADE_DATE),
            )
        val transactionsB =
            arrayOf(
                createTransactionInput("3", "2018-10-01"),
                createTransactionInput("34", "2017-01-01"),
            )

        postTransaction(portfolioA.id, transactionsA)
        postTransaction(portfolioB.id, transactionsB)
    }

    private fun createPortfolio(
        type: String,
        name: String,
    ): Portfolio {
        return bcMvcHelper.portfolio(PortfolioInput(type, name, currency = Constants.NZD.code))
    }

    private fun createTransactionInput(
        callerId: String,
        tradeDate: String,
        tradePortfolioRate: BigDecimal = BigDecimal.TEN,
    ): TrnInput {
        return TrnInput(
            CallerRef(batch = BigDecimal.ZERO.toString(), callerId = callerId),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getDate(tradeDate),
            price = BigDecimal.TEN,
            tradePortfolioRate = tradePortfolioRate,
        )
    }

    private fun postTransaction(
        portfolioId: String,
        transactions: Array<TrnInput>,
    ) {
        bcMvcHelper.postTrn(TrnRequest(portfolioId, transactions))
    }

    @Test
    fun is_TrnForPortfolioInRangeFound() {
        // All transactions are now in place.
        assertThat(
            objectMapper.readValue(
                mockMvc.perform(
                    MockMvcRequestBuilders.get(
                        "${BcMvcHelper.PORTFOLIO_ROOT}/asset/{assetId}/{tradeDate}",
                        msft.id,
                        BcMvcHelper.TRADE_DATE,
                    )
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn().response.contentAsString,
                PortfoliosResponse::class.java,
            ).data,
        ).hasSize(2)
        assertThat(
            portfolioService.findWhereHeld(
                msft.id,
                dateUtils.getDate("2016-01-01"),
            ).data,
        ).hasSize(1)
        assertThat(portfolioService.findWhereHeld(msft.id, null).data).hasSize(2)
    }
}
