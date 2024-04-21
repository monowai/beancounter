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
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
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
    fun setupObjects(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig,
    ) {
        token = mockAuthConfig.getUserToken(SystemUser(auth0 = "auth0"))
        bcMvcHelper = BcMvcHelper(mockMvc, token)
        RegistrationUtils.registerUser(mockMvc, token)
        msft =
            bcMvcHelper.asset(
                AssetRequest(Constants.msftInput),
            )
        // Creating in reverse trade order and assert retrieved in Sort Order.
        bcMvcHelper.postTrn(
            TrnRequest(
                bcMvcHelper.portfolio(
                    PortfolioInput("PCA", "PCA-NAME", currency = Constants.NZD.code),
                ).id,
                arrayOf(
                    TrnInput(
                        CallerRef(batch = BigDecimal.ZERO.toString(), callerId = "1"),
                        msft.id,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.TEN,
                        tradeDate = dateUtils.getDate(BcMvcHelper.TRADE_DATE),
                        price = BigDecimal.TEN,
                    ),
                    TrnInput(
                        CallerRef(batch = BigDecimal.ZERO.toString(), callerId = "2"),
                        msft.id,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.TEN,
                        tradeDate = dateUtils.getDate("2016-01-01"),
                        price = BigDecimal.TEN,
                        tradePortfolioRate = BigDecimal.ONE,
                    ),
                ),
            ),
        )
        bcMvcHelper.postTrn(
            TrnRequest(
                bcMvcHelper.portfolio(
                    PortfolioInput("PCB", "PCB-NAME", currency = Constants.NZD.code),
                ).id,
                arrayOf(
                    TrnInput(
                        CallerRef(batch = BigDecimal.ZERO.toString(), callerId = "3"),
                        msft.id,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.TEN,
                        price = BigDecimal.TEN,
                        tradeDate = dateUtils.getDate("2018-10-01"),
                    ),
                    TrnInput(
                        CallerRef(batch = BigDecimal.ZERO.toString(), callerId = "34"),
                        msft.id,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.TEN,
                        tradeDate = dateUtils.getDate("2017-01-01"),
                        price = BigDecimal.TEN,
                    ),
                ),
            ),
        )
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
