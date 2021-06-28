package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.Objects

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
/**
 * TRN Mvc Controller Tests
 */
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

    @MockBean
    private lateinit var figiProxy: FigiProxy
    private lateinit var token: Jwt
    private lateinit var mockMvc: MockMvc
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    // Test Constants
    private lateinit var getNasdaq: Market
    private lateinit var getMsft: AssetInput
    private lateinit var getAppl: AssetInput
    private val trnsRoot = "/trns"
    private val urlPortfolioId = "$trnsRoot/portfolio/{portfolioId}"
    // End Test Constants

    @BeforeEach
    fun setupObjects() {
        getNasdaq = marketService.getMarket(marketCode = "NASDAQ")
        getMsft = getAssetInput(getNasdaq.code, MSFT.code)
        getAppl = getAssetInput(getNasdaq.code, AAPL.code)
    }

    @Autowired
    fun mockServices() {
        assertThat(currencyService.currencies).isNotEmpty
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
        token = TokenUtils().getUserToken(Constants.systemUser)
        RegistrationUtils.registerUser(mockMvc, token)
        assertThat(figiProxy).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_EmptyResponseValid() {
        val portfolio = portfolio(PortfolioInput("BLAH", "is_EmptyResponseValid", currency = NZD.code))
        val mvcResult = mockMvc.perform(
            get(urlPortfolioId, portfolio.id)
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

    private val tradeCurrency = USD.code

    @Test
    @Throws(Exception::class)
    fun is_ExistingDividendFound() {
        val msft = asset(
            AssetRequest(MSFT.code, getMsft)
        )
        val portfolioA = portfolio(
            PortfolioInput("DIV-TEST", "is_ExistingDividendFound", currency = NZD.code)
        )
        assertThat(msft.id).isNotNull
        // Creating in random order and assert retrieved in Sort Order.
        val trnInput = TrnInput(
            CallerRef(batch = "DIV-TEST", callerId = "1"),
            msft.id,
            TrnType.DIVI,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate("2020-03-10"),
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
        // divi.tradeDate = dateUtils.getDate("2020-03-09")
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
    @Throws(Exception::class)
    fun is_TrnForPortfolioInRangeFound() {
        val msft = asset(AssetRequest(MSFT.code, getMsft))

        assertThat(msft.id).isNotNull
        val (id) = portfolio(PortfolioInput("PFA", "is_TrnForPortfolioInRangeFound", currency = NZD.code))
        // Creating in random order and assert retrieved in Sort Order.

        var trnInput = TrnInput(
            CallerRef(batch = "0", callerId = "1"),
            msft.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate("2018-01-01"),
            price = BigDecimal.TEN
        )

        trnInput.tradePortfolioRate = BigDecimal.ONE

        var trnInputB = TrnInput(
            CallerRef(batch = "0", callerId = "2"),
            msft.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate("2016-01-01"),
            price = BigDecimal.TEN
        )
        trnInputB.tradePortfolioRate = BigDecimal.ONE
        var trnInputs = arrayOf(trnInput, trnInputB)
        var trnRequest = TrnRequest(id, trnInputs)
        postTrn(trnRequest)

        trnInput = TrnInput(
            CallerRef(batch = "0", callerId = "3"),
            msft.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            price = BigDecimal.TEN,
            tradeDate = dateUtils.getDate("2018-10-01")

        )
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputB = TrnInput(
            CallerRef(batch = "0", callerId = "34"),
            msft.id,
            TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getDate("2017-01-01"),
            price = BigDecimal.TEN
        )
        trnInputB.tradePortfolioRate = BigDecimal.ONE

        trnInputs = arrayOf(trnInput, trnInputB)
        val (id1) = portfolio(PortfolioInput("PFB", "is_TrnForPortfolioInRangeFound", currency = NZD.code))
        trnRequest = TrnRequest(id1, trnInputs)
        postTrn(trnRequest)

        // All transactions are now in place.
        val response = mockMvc.perform(
            get(
                "/portfolios/asset/{assetId}/{tradeDate}",
                msft.id, "2018-01-01"
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
    @Throws(Exception::class)
    fun is_PersistRetrieveAndPurge() {
        val msft = asset(
            AssetRequest(MSFT.code, getMsft)
        )
        assertThat(msft.id).isNotNull
        val aapl = asset(
            AssetRequest(AAPL.code, getAppl)
        )
        assertThat(aapl.id).isNotNull
        val portfolio = portfolio(
            PortfolioInput("Twix", "is_PersistRetrieveAndPurge", currency = NZD.code)
        )
        // Creating in random order and assert retrieved in Sort Order.
        val tradeDate = "2018-01-01"
        val trnInputA = TrnInput(
            CallerRef(callerId = "1"),
            msft.id,
            TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getDate(tradeDate),
            tradeCurrency = tradeCurrency,
            price = BigDecimal.TEN,
        )

        trnInputA.tradePortfolioRate = BigDecimal.ONE

        val trnInputB = TrnInput(
            CallerRef(null, null, "3"),
            aapl.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate(tradeDate),
            price = BigDecimal.TEN
        )

        trnInputB.tradePortfolioRate = BigDecimal.ONE

        val earlyTradeDate = "2017-01-01"
        val trnInputC = TrnInput(
            CallerRef(callerId = "2"),
            msft.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate(earlyTradeDate),
            price = BigDecimal.TEN
        )
        trnInputC.tradePortfolioRate = BigDecimal.ONE

        val trnInputD = TrnInput(
            CallerRef(callerId = "4"),
            aapl.id,
            TrnType.BUY,
            BigDecimal.TEN,
            tradeCurrency,
            dateUtils.getDate(earlyTradeDate),
            price = BigDecimal.TEN
        )
        trnInputD.tradePortfolioRate = BigDecimal.ONE

        val trnRequest = TrnRequest(portfolio.id, arrayOf(trnInputA, trnInputB, trnInputC, trnInputD))
        val postResult = postTrn(trnRequest)
        var trnResponse: TrnResponse = objectMapper
            .readValue(postResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(4)
        for ((_, asset) in trnResponse.data) {
            assertThat(asset).isNotNull
        }
        assertThat(trnResponse.data).isNotNull.isNotEmpty
        assertThat(trnResponse.data.iterator().next().portfolio).isNotNull
        val portfolioId = Objects.requireNonNull(
            trnResponse.data.iterator().next().portfolio
        ).id

        // General Query
        val findByQuery = mockMvc.perform(
            post("$trnsRoot/query")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TrustedTrnQuery(portfolio, assetId = msft.id)))
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        val queryResponse = objectMapper
            .readValue(findByQuery.response.contentAsString, TrnResponse::class.java)
        assertThat(queryResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions

        // Find by Portfolio, sorted by assetId and then Date
        var mvcResult = mockMvc.perform(
            get(urlPortfolioId, portfolioId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(trnRequest))
                .contentType(APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        trnResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(4)
        var i = 4
        // Verify the sort order - asset.code, tradeDate
        for (trn in trnResponse.data) {
            assertThat(trn.callerRef).isNotNull.hasNoNullFieldsOrProperties()
            assertThat(trn.callerRef!!.callerId).isNotNull
            assertThat(trn.callerRef!!.callerId == i--.toString())
            assertThat(trn.asset).isNotNull
            assertThat(trn.id).isNotNull
        }
        val trn = trnResponse.data.iterator().next()
        val getTrnId = "$trnsRoot/{portfolioId}/{trnId}"

        // BadRequest for illegal ID
        mockMvc.perform(
            get(getTrnId, portfolio.id, "illegalId")
                .contentType(APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))

        // Find by PrimaryKey
        mvcResult = mockMvc.perform(
            get(getTrnId, portfolio.id, trn.id)
                .contentType(APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        trnResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(1)

        // Find by portfolio and asset
        val findByAsset = mockMvc.perform(
            get("$trnsRoot/{portfolioId}/asset/{assetId}/trades", portfolioId, msft.id)
                .contentType(APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        trnResponse = objectMapper
            .readValue(findByAsset.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions

        // Most recent transaction first (display purposes
        val toDelete = trnResponse.data.iterator().next()
        assertThat(toDelete.tradeDate).isEqualTo(tradeDate)

        // Delete illegal transaction by primary key
        val getTrnById = "$trnsRoot/{trnId}"
        mockMvc.perform(
            MockMvcRequestBuilders.delete(getTrnById, "illegalId")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))

        // Delete a single transaction by primary key
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.delete(getTrnById, toDelete.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsByteArray, TrnResponse::class.java)
        assertThat(data).hasSize(1)
        val deleted = data.iterator().next()
        assertThat(deleted.id).isEqualTo(toDelete.id)

        // Delete all remaining transactions for the Portfolio
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.delete(urlPortfolioId, portfolioId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        assertThat(mvcResult.response.contentAsString).isNotNull.isEqualTo("3")
    }

    private fun postTrn(trnRequest: TrnRequest) = mockMvc.perform(
        post(trnsRoot)
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(trnRequest))
            .contentType(APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
        .andReturn()

    @Throws(Exception::class)
    private fun portfolio(portfolio: PortfolioInput): Portfolio {
        val createRequest = PortfoliosRequest(setOf(portfolio))
        val portfolioResult = mockMvc.perform(
            post("/portfolios", portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(ObjectMapper().writeValueAsBytes(createRequest))
                .contentType(APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        return data.iterator().next()
    }

    @Throws(Exception::class)
    private fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult = mockMvc.perform(
            post("/assets/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(assetRequest))
                .contentType(APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }
}
