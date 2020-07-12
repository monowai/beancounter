package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.*
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.model.*
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("test")
@Tag("slow")
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

    @Autowired
    fun mockServices() {
        assertThat(currencyService.currencies).isNotEmpty
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        val user = SystemUser("TrnMvcTest", "user@testing.com")
        token = TokenUtils.getUserToken(user)
        RegistrationUtils.registerUser(mockMvc, token)
        assertThat(figiProxy).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_EmptyResponseValid() {
        val portfolio = portfolio(PortfolioInput("BLAH", "NZD Portfolio", "NZD"))
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/trns/portfolio/{portfolioId}", portfolio.id)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val body = mvcResult.response.contentAsString
        assertThat(body).isNotNull()
        val (data) = objectMapper.readValue(body, TrnResponse::class.java)
        assertThat(data).isNotNull.hasSize(0)
    }

    @Test
    @Throws(Exception::class)
    fun is_ExistingDividendFound() {
        val nasdaq = marketService.getMarket("NASDAQ")
        val msft = asset(AssetRequest("msft",
                getAssetInput(nasdaq.code, "MSFT")))
        val portfolioA = portfolio(
                PortfolioInput("DIV-TEST", "NZD Portfolio", "NZD"))
        assertThat(msft.id).isNotNull()
        // Creating in random order and assert retrieved in Sort Order.
        val existingTrns: MutableCollection<TrnInput> = ArrayList()
        val trnInput = TrnInput(
                CallerRef(null, "DIV-TEST", "1"),
                msft.id!!,
                TrnType.DIVI,  BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2020-03-10")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        existingTrns.add(trnInput)
        val trnRequest = TrnRequest(portfolioA.id, existingTrns)
        trnService.save(portfolioA, trnRequest)
        val divi = existingTrns.iterator().next()

        val trustedTrnEvent = TrustedTrnEvent(portfolioA, divi)
        assertThat(trnService.isExists(trustedTrnEvent)).isTrue()

        // Record date is earlier than an existing trn trade date
        divi.tradeDate = dateUtils.getDate("2020-02-25")
        assertThat(trnService.isExists(trustedTrnEvent))
                .isTrue() // Within 20 days of proposed trade date
        divi.tradeDate = dateUtils.getDate("2020-03-09")
        assertThat(trnService.isExists(trustedTrnEvent))
                .isTrue() // Within 20 days of proposed trade date

        val findByAsset = mockMvc.perform(
                MockMvcRequestBuilders.get("/trns/{portfolioId}/asset/{assetId}/events",
                        portfolioA.id, msft.id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val trnResponse = objectMapper
                .readValue(findByAsset.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(1) // 1 MSFT dividend

    }

    @Test
    @Throws(Exception::class)
    fun is_TrnForPortfolioInRangeFound() {
        val nasdaq = marketService.getMarket("NASDAQ")
        val msft = asset(
                AssetRequest("MSFT", getAssetInput(nasdaq.code, "MSFT")))
        assertThat(msft.id).isNotNull()
        val (id) = portfolio(PortfolioInput("PFA", "NZD Portfolio", "NZD"))
        // Creating in random order and assert retrieved in Sort Order.
        val trnInputs: MutableCollection<TrnInput> = ArrayList()

        var trnInput = TrnInput(
                CallerRef(null, "0", "1"),
                msft.id!!, TrnType.BUY,  BigDecimal.TEN)

        trnInput.tradeDate = dateUtils.getDate("2018-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)

        trnInput = TrnInput(CallerRef(null, "0", "2"),
                msft.id!!,
                TrnType.BUY, BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2016-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)
        var trnRequest = TrnRequest(id, trnInputs)
        mockMvc.perform(
                MockMvcRequestBuilders.post("/trns")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsBytes(trnRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        trnInputs.clear()
        trnInput = TrnInput(CallerRef(null, "0", "3"),
                msft.id!!,
                TrnType.BUY,  BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2018-10-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)
        trnInput = TrnInput(CallerRef(null, "0", "34"),
                msft.id!!,
                TrnType.BUY,  BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2017-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)
        val (id1) = portfolio(PortfolioInput("PFB", "NZD Portfolio", "NZD"))
        trnRequest = TrnRequest(id1, trnInputs)
        mockMvc.perform(
                MockMvcRequestBuilders.post("/trns")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsBytes(trnRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        // All transactions are now in place.
        val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/portfolios/asset/{assetId}/{tradeDate}",
                        msft.id, "2018-01-01")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        var portfolios: PortfoliosResponse = objectMapper
                .readValue(response.response.contentAsString, PortfoliosResponse::class.java)
        assertThat(portfolios.data).hasSize(2)
        portfolios = portfolioService.findWhereHeld(msft.id,
                dateUtils.getDate("2016-01-01"))
        assertThat(portfolios.data).hasSize(1)
        portfolios = portfolioService.findWhereHeld(msft.id, null)
        assertThat(portfolios.data).hasSize(2)
    }

    @Test
    @Throws(Exception::class)
    fun is_PersistRetrieveAndPurge() {
        val nasdaq = marketService.getMarket("NASDAQ")
        val msft = asset(
                AssetRequest("msft", getAssetInput(nasdaq.code, "MSFT")))
        assertThat(msft.id).isNotNull()
        val aapl = asset(
                AssetRequest("aapl", getAssetInput(nasdaq.code, "AAPL")))
        assertThat(aapl.id).isNotNull()
        val portfolio = portfolio(
                PortfolioInput("Twix", "NZD Portfolio", "NZD"))
        // Creating in random order and assert retrieved in Sort Order.
        val trnInputs: MutableCollection<TrnInput> = ArrayList()
        var trnInput = TrnInput(CallerRef(null, null, "1"),
                msft.id!!,
                TrnType.BUY,  BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2018-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)

        trnInput = TrnInput(CallerRef(null, null, "3"),
                aapl.id!!,
                TrnType.BUY, BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2018-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)

        trnInput = TrnInput(CallerRef(null, null, "2"),
                msft.id!!,
                TrnType.BUY, BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2017-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)

        trnInput = TrnInput(CallerRef(null, null, "4"),
                aapl.id!!,
                TrnType.BUY, BigDecimal.TEN)
        trnInput.tradeDate = dateUtils.getDate("2017-01-01")
        trnInput.price = BigDecimal.TEN
        trnInput.tradeCurrency = nasdaq.currency.code
        trnInput.tradePortfolioRate = BigDecimal.ONE
        trnInputs.add(trnInput)

        val trnRequest = TrnRequest(portfolio.id, trnInputs)
        val postResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/trns")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsBytes(trnRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        var trnResponse: TrnResponse = objectMapper
                .readValue(postResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(4)
        for ((_, asset) in trnResponse.data) {
            assertThat(asset).isNotNull
        }
        assertThat(trnResponse.data).isNotNull.isNotEmpty
        assertThat(trnResponse.data.iterator().next().portfolio).isNotNull
        val portfolioId = Objects.requireNonNull(
                trnResponse.data.iterator().next().portfolio).id

        // Find by Portfolio, sorted by assetId and then Date
        var mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/trns/portfolio/{portfolioId}", portfolioId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsBytes(trnRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        trnResponse = objectMapper
                .readValue(mvcResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(4)
        var i = 4
        // Verify the sort order - asset.code, tradeDate
        for (trn in trnResponse.data) {
            assertThat(trn.callerRef).isNotNull.hasNoNullFieldsOrProperties()
            assertThat(trn.callerRef!!.callerId).isNotNull()
            assertThat(trn.callerRef!!.callerId == i--.toString())
            assertThat(trn.asset).isNotNull
            assertThat(trn.id).isNotNull()
        }
        val trn = trnResponse.data.iterator().next()
        // Find by PrimaryKey
        mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/trns/{portfolioId}/{trnId}",
                        portfolio.id,
                        trn.id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        trnResponse = objectMapper
                .readValue(mvcResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(1)

        // Find by portfolio and asset
        val findByAsset = mockMvc.perform(
                MockMvcRequestBuilders.get("/trns/{portfolioId}/asset/{assetId}/trades",
                        portfolioId, msft.id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        trnResponse = objectMapper
                .readValue(findByAsset.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions

        // Most recent transaction first (display purposes
        val toDelete = trnResponse.data.iterator().next()
        assertThat(toDelete.tradeDate).isEqualTo("2018-01-01")

        // Delete a single transaction by primary key
        mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.delete("/trns/{trnId}", toDelete.id)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsByteArray, TrnResponse::class.java)
        assertThat(data).hasSize(1)
        val deleted = data.iterator().next()
        assertThat(deleted.id).isEqualTo(toDelete.id)

        // Delete all remaining transactions for the Portfolio
        mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.delete("/trns/portfolio/{portfolioId}", portfolioId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(mvcResult.response.contentAsString).isNotNull().isEqualTo("3")
    }

    @Throws(Exception::class)
    private fun portfolio(portfolio: PortfolioInput): Portfolio {
        val createRequest = PortfoliosRequest(setOf(portfolio))
        val portfolioResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/portfolios", portfolio.code)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(ObjectMapper().writeValueAsBytes(createRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        return data.iterator().next()
    }

    @Throws(Exception::class)
    private fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/assets/")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .content(objectMapper.writeValueAsBytes(assetRequest))
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }
}