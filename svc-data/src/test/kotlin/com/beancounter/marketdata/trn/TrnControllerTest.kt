package com.beancounter.marketdata.trn

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.MockEnricher
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.tradeDate
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.trnsRoot
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.uriTrnForPortfolio
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * TRN Mvc Controller API Tests
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
class TrnControllerTest {
    private val authorityRoleConverter = AuthorityRoleConverter()
    private val dateUtils = DateUtils()

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @MockBean
    private lateinit var figiProxy: FigiProxy
    private lateinit var token: Jwt
    private lateinit var mockMvc: MockMvc
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun setupObjects() {
        enrichmentFactory.register(MockEnricher())
        assertThat(currencyService.currencies).isNotEmpty
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
        token = TokenUtils().getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        RegistrationUtils.registerUser(mockMvc, token)
        assertThat(figiProxy).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_EmptyResponseValid() {
        val portfolio = bcMvcHelper.portfolio(
            PortfolioInput(
                "BLAH", "is_EmptyResponseValid",
                currency = NZD.code
            )
        )
        val mvcResult = mockMvc.perform(
            get(uriTrnForPortfolio, portfolio.id)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        val body = mvcResult.response.contentAsString
        assertThat(body).isNotNull
        val (data) = objectMapper.readValue(body, TrnResponse::class.java)
        assertThat(data).isNotNull.hasSize(0)
    }

    @Test
    @Throws(Exception::class)
    fun is_ExistingDividendFound() {
        val msft = bcMvcHelper.asset(
            AssetRequest(MSFT.code, msftInput)
        )
        val portfolioA = bcMvcHelper.portfolio(
            PortfolioInput("DIV-TEST", "is_ExistingDividendFound", currency = NZD.code)
        )
        assertThat(msft.id).isNotNull
        // Creating in random order and assert retrieved in Sort Order.
        val trnInput = TrnInput(
            CallerRef(batch = "DIV-TEST", callerId = "1"),
            msft.id,
            trnType = TrnType.DIVI,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradeDate = dateUtils.getDate("2020-03-10"),
            price = BigDecimal.TEN
        )
        trnInput.tradePortfolioRate = BigDecimal.ONE
        val existingTrns = arrayOf(trnInput)
        val trnRequest = TrnRequest(portfolioA.id, existingTrns)
        trnService.save(portfolioA, trnRequest)
        val divi = existingTrns.iterator().next()

        val trustedTrnEvent = TrustedTrnEvent(portfolioA, divi)
        assertThat(trnService.existing(trustedTrnEvent)).isNotNull.isNotEmpty

        // Record date is earlier than an existing trn trade date
        assertThat(trnService.existing(trustedTrnEvent))
            .isNotNull.isNotEmpty // Within 20 days of proposed trade date
        assertThat(trnService.existing(trustedTrnEvent))
            .isNotNull.isNotEmpty // Within 20 days of proposed trade date

        val findByAsset = mockMvc.perform(
            get(
                "$trnsRoot/{portfolioId}/asset/{assetId}/events",
                portfolioA.id, msft.id
            )
                .contentType(APPLICATION_JSON)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.content()
                    .contentType(APPLICATION_JSON)
            )
            .andReturn()
        val trnResponse = objectMapper
            .readValue(findByAsset.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(1) // 1 MSFT dividend
    }

    @Test
    fun is_findThrowingForIllegalTrnId() {
        val portfolio = bcMvcHelper.portfolio(
            PortfolioInput(
                "ILLEGAL", "is_findThrowingForIllegalTrnId",
                currency = NZD.code
            )
        )

        mockMvc.perform(
            get("$trnsRoot/{portfolioId}/{trnId}", portfolio.id, "x123x")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
    }

    @Test
    @Throws(Exception::class)
    fun is_TrnForPortfolioInRangeFound() {
        val msft = bcMvcHelper.asset(AssetRequest(MSFT.code, msftInput))

        assertThat(msft.id).isNotNull
        val (id) = bcMvcHelper.portfolio(PortfolioInput("PFA", "is_TrnForPortfolioInRangeFound", currency = NZD.code))
        // Creating in random order and assert retrieved in Sort Order.

        var trnInput = TrnInput(
            CallerRef(batch = "0", callerId = "1"),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradeDate = dateUtils.getDate(tradeDate),
            price = BigDecimal.TEN
        )

        trnInput.tradePortfolioRate = BigDecimal.ONE

        var trnInputB = TrnInput(
            CallerRef(batch = "0", callerId = "2"),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradeDate = dateUtils.getDate("2016-01-01"),
            price = BigDecimal.TEN
        )
        trnInputB.tradePortfolioRate = BigDecimal.ONE
        var trnInputs = arrayOf(trnInput, trnInputB)
        var trnRequest = TrnRequest(id, trnInputs)
        bcMvcHelper.postTrn(trnRequest)

        trnInput = TrnInput(
            CallerRef(batch = "0", callerId = "3"),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            price = BigDecimal.TEN,
            tradeDate = dateUtils.getDate("2018-10-01")

        )
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputB = TrnInput(
            CallerRef(batch = "0", callerId = "34"),
            msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getDate("2017-01-01"),
            price = BigDecimal.TEN
        )
        trnInputB.tradePortfolioRate = BigDecimal.ONE

        trnInputs = arrayOf(trnInput, trnInputB)
        val (id1) = bcMvcHelper.portfolio(PortfolioInput("PFB", "is_TrnForPortfolioInRangeFound", currency = NZD.code))
        trnRequest = TrnRequest(id1, trnInputs)
        bcMvcHelper.postTrn(trnRequest)

        // All transactions are now in place.
        val response = mockMvc.perform(
            get(
                "/portfolios/asset/{assetId}/{tradeDate}",
                msft.id, tradeDate
            )
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        var portfolios: PortfoliosResponse = objectMapper
            .readValue(response.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfolios.data).hasSize(2)
        portfolios = portfolioService.findWhereHeld(
            msft.id,
            dateUtils.getDate("2016-01-01")
        )
        assertThat(portfolios.data).hasSize(1)
        portfolios = portfolioService.findWhereHeld(msft.id, null)
        assertThat(portfolios.data).hasSize(2)
    }

    @Test
    fun is_deleteThrowingForIllegalTrnId() {
        mockMvc.perform(
            delete("$trnsRoot/{trnId}", "illegalTrnId")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
    }
}
