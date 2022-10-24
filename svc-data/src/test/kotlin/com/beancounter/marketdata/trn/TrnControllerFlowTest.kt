package com.beancounter.marketdata.trn

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.aaplInput
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.tradeDate
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.trnsRoot
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.uriTrnForPortfolio
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.util.Objects

/**
 * Splits out the general flow of transactions to verify they work as expected through the trn lifecycle.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockAuth
@AutoConfigureMockMvc
class TrnControllerFlowTest {

    private val dateUtils = DateUtils()

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @MockBean
    private lateinit var figiProxy: FigiProxy

    @MockBean
    private lateinit var fxTransactions: FxTransactions
    private lateinit var token: Jwt
    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun setupObjects() {
        enrichmentFactory.register(DefaultEnricher())
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)
        bcMvcHelper.registerUser()
    }

    @Test
    @Throws(Exception::class)
    fun is_PersistRetrieveAndPurge() {
        val msft = asset(
            AssetRequest(msftInput, MSFT.code)
        )
        assertThat(msft.id).isNotNull
        val aapl = asset(
            AssetRequest(aaplInput, AAPL.code)
        )
        assertThat(aapl.id).isNotNull
        val portfolio = bcMvcHelper.portfolio(
            PortfolioInput("Twix", "is_PersistRetrieveAndPurge", currency = NZD.code)
        )
        // Creating in random order and assert retrieved in Sort Order.
        val tradeDate = tradeDate
        val trnInputA = TrnInput(
            CallerRef(callerId = "1"),
            assetId = msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeDate = dateUtils.getDate(tradeDate),
            price = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradePortfolioRate = BigDecimal.ONE,
        )

        val trnInputB = TrnInput(
            CallerRef(callerId = "3"),
            assetId = aapl.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradePortfolioRate = BigDecimal.ONE,
            tradeDate = dateUtils.getDate(tradeDate),
            price = BigDecimal.TEN
        )

        val earlyTradeDate = "2017-01-01"
        val trnInputC = TrnInput(
            CallerRef(callerId = "2"),
            assetId = msft.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradePortfolioRate = BigDecimal.ONE,
            tradeDate = dateUtils.getDate(earlyTradeDate),
            price = BigDecimal.TEN
        )

        val trnInputD = TrnInput(
            CallerRef(callerId = "4"),
            assetId = aapl.id,
            trnType = TrnType.BUY,
            quantity = BigDecimal.TEN,
            tradeCurrency = USD.code,
            tradeBaseRate = null,
            tradeCashRate = null,
            tradePortfolioRate = BigDecimal.ONE,
            tradeDate = dateUtils.getDate(earlyTradeDate),
            price = BigDecimal.TEN
        )

        val trnRequest = TrnRequest(portfolio.id, arrayOf(trnInputA, trnInputB, trnInputC, trnInputD))
        val postResult = bcMvcHelper.postTrn(trnRequest)
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
            MockMvcRequestBuilders.post("$trnsRoot/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TrustedTrnQuery(portfolio, assetId = msft.id)))
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(bcMvcHelper.token)

                )
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val queryResponse = objectMapper
            .readValue(findByQuery.response.contentAsString, TrnResponse::class.java)
        assertThat(queryResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions

        // Find by Portfolio, sorted by assetId and then Date
        var mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(uriTrnForPortfolio, portfolioId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
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
            assertThat(trn.callerRef!!.callerId).isNotNull
            assertThat(trn.callerRef!!.callerId == i--.toString())
            assertThat(trn.asset).isNotNull
            assertThat(trn.id).isNotNull
        }
        val trn = trnResponse.data.iterator().next()
        val getTrnId = "$trnsRoot/{portfolioId}/{trnId}"

        // BadRequest for illegal ID
        mockMvc.perform(
            MockMvcRequestBuilders.get(getTrnId, portfolio.id, "illegalId")
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))

        // Find by PrimaryKey
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(getTrnId, portfolio.id, trn.id)
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        trnResponse = objectMapper
            .readValue(mvcResult.response.contentAsString, TrnResponse::class.java)
        assertThat(trnResponse.data).isNotEmpty.hasSize(1)

        // Find by portfolio and asset
        val findByAsset = mockMvc.perform(
            MockMvcRequestBuilders.get("$trnsRoot/{portfolioId}/asset/{assetId}/trades", portfolioId, msft.id)
                .contentType(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
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
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))

        // Delete a single transaction by primary key
        mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.delete(getTrnById, toDelete.id)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
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
            MockMvcRequestBuilders.delete(uriTrnForPortfolio, portfolioId)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        assertThat(mvcResult.response.contentAsString).isNotNull.isEqualTo("3")
    }

    @Throws(Exception::class)
    private fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/assets/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
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
