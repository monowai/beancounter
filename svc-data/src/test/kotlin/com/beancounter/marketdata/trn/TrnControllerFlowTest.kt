package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.aaplInput
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.TRADE_DATE
import com.beancounter.marketdata.utils.TRNS_ROOT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

const val TRNS_BY_ID = "$TRNS_ROOT/{trnId}"

/**
 * Test suite for TrnController to ensure complete transaction lifecycle management works correctly.
 *
 * This class tests:
 * - Transaction creation with valid input data
 * - Transaction retrieval by various criteria (ID, portfolio, asset, date range)
 * - Transaction deletion (single and bulk operations)
 * - Error handling for invalid inputs and non-existent resources
 * - Authorization and security validation
 * - Data consistency and sorting behavior
 *
 * Tests use Spring Boot Test with MockMvc to simulate HTTP requests
 * and verify the complete transaction workflow from creation to deletion.
 */
@SpringMvcDbTest
class TrnControllerFlowTest(
    @param:Autowired var mockMvc: MockMvc,
    @param:Autowired var mockAuthConfig: MockAuthConfig
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    private lateinit var token: Jwt
    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var msft: Asset
    private lateinit var aapl: Asset
    private lateinit var trnInputA: TrnInput
    private lateinit var trnInputB: TrnInput
    private lateinit var trnInputC: TrnInput
    private lateinit var trnInputD: TrnInput

    @BeforeEach
    fun configure() {
        assertThat(figiProxy).isNotNull
        assertThat(fxTransactions).isNotNull
        enrichmentFactory.register(DefaultEnricher())
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                token
            )
        bcMvcHelper.registerUser()
        msft =
            asset(
                AssetRequest(
                    msftInput,
                    MSFT.code
                )
            )
        assertThat(msft.id).isNotNull
        aapl =
            asset(
                AssetRequest(
                    aaplInput,
                    AAPL.code
                )
            )
        assertThat(aapl.id).isNotNull
    }

    private fun createTestTransactions() {
        // Creating in random order and assert retrieved in Sort Order.
        trnInputA =
            TrnInput(
                CallerRef(callerId = "1"),
                assetId = msft.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeDate = dateUtils.getFormattedDate(TRADE_DATE),
                price = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                status = TrnStatus.SETTLED
            )

        trnInputB =
            TrnInput(
                CallerRef(callerId = "3"),
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate(TRADE_DATE),
                price = BigDecimal.TEN,
                status = TrnStatus.SETTLED
            )

        val earlyTradeDate = "2017-01-01"
        trnInputC =
            TrnInput(
                CallerRef(callerId = "2"),
                assetId = msft.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate(earlyTradeDate),
                price = BigDecimal.TEN,
                status = TrnStatus.SETTLED
            )

        trnInputD =
            TrnInput(
                CallerRef(callerId = "4"),
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeBaseRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeDate = dateUtils.getFormattedDate(earlyTradeDate),
                price = BigDecimal.TEN,
                status = TrnStatus.SETTLED
            )
    }

    @Test
    fun `should return bad request when deleting non-existent transaction`() {
        // Given a non-existent transaction ID
        val nonExistentId = "illegalId"

        // When attempting to delete the non-existent transaction
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete(TRNS_BY_ID, nonExistentId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            )
    }

    @Test
    fun `should persist retrieve and delete transactions in complete lifecycle`() {
        createTestTransactions()
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "Twix",
                    "Name",
                    currency = NZD.code
                )
            )
        val trnRequest =
            TrnRequest(
                portfolio.id,
                listOf(
                    trnInputA,
                    trnInputB,
                    trnInputC,
                    trnInputD
                )
            )
        val trnResponse: TrnResponse =
            objectMapper.readValue(
                bcMvcHelper.postTrn(trnRequest).response.contentAsString,
                TrnResponse::class.java
            )
        assertThat(trnResponse.data).hasSize(trnRequest.data.size)

        // General Query
        val queryResponse =
            objectMapper
                .readValue(
                    mockMvc
                        .perform(
                            MockMvcRequestBuilders
                                .post("$TRNS_ROOT/query")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                    objectMapper.writeValueAsString(
                                        TrustedTrnQuery(
                                            portfolio,
                                            assetId = msft.id
                                        )
                                    )
                                ).with(
                                    SecurityMockMvcRequestPostProcessors.jwt().jwt(bcMvcHelper.token)
                                )
                        ).andExpect(MockMvcResultMatchers.status().isOk)
                        .andExpect(
                            MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON)
                        ).andReturn()
                        .response.contentAsString,
                    TrnResponse::class.java
                )

        assertThat(queryResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions
        val mvcResult = findSortedByAssetAndTradeDate(portfolio)
        val firstId =
            checkResponseSortOrder(
                mvcResult,
                trnRequest.data.size
            ).data.iterator().next().id

        // Find by PrimaryKey
        verifyFoundByPk(
            firstId
        )
        val allTrades =
            findTrades(
                portfolio,
                msft,
                token
            )

        // Find by portfolio and asset
        assertThat(allTrades.data).isNotEmpty.hasSize(2) // 2 MSFT transactions

        // Most recent transaction first
        val (id, _, foundTradeDate) = allTrades.data.iterator().next()
        assertThat(foundTradeDate).isEqualTo(TRADE_DATE)

        // Delete a single transaction by primary key
        assertThat(
            deleteTransaction(
                id,
                token
            ).data.iterator().next()
        ).isEqualTo(id)

        // Delete all remaining transactions for the Portfolio
        assertThat(
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete(
                            "$TRNS_ROOT/portfolio/{portfolioId}",
                            portfolio.id
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .response.contentAsString
        ).isNotNull.isEqualTo("3")
    }

    private fun deleteTransaction(
        id: String,
        token: Jwt
    ): TrnDeleteResponse =
        objectMapper.readValue(
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete(
                            TRNS_BY_ID,
                            id
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .response.contentAsByteArray,
            TrnDeleteResponse::class.java
        )

    private fun findSortedByAssetAndTradeDate(portfolio: Portfolio): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(
                        "$TRNS_ROOT/portfolio/{portfolioId}/{asAt}",
                        portfolio.id,
                        dateUtils.today()
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    private fun verifyFoundByPk(id: String) {
        val pkResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            TRNS_BY_ID,
                            id
                        ).contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    pkResult.response.contentAsString,
                    TrnResponse::class.java
                ).data
        ).isNotEmpty.hasSize(1)
    }

    private fun findTrades(
        portfolio: Portfolio,
        asset: Asset,
        token: Jwt
    ): TrnResponse {
        val findByAsset =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$TRNS_ROOT/{portfolioId}/asset/{assetId}/trades",
                            portfolio.id,
                            asset.id
                        ).contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        return objectMapper
            .readValue(
                findByAsset.response.contentAsString,
                TrnResponse::class.java
            )
    }

    private fun checkResponseSortOrder(
        mvcResult: MvcResult,
        i: Int
    ): TrnResponse {
        var i1 = i
        val sortedResponse =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    TrnResponse::class.java
                )
        assertThat(sortedResponse.data).isNotEmpty.hasSize(i1)
        // Verify the sort order - asset.code, tradeDate
        for (trn in sortedResponse.data) {
            assertThat(trn.callerRef).isNotNull.hasNoNullFieldsOrProperties()
            assertThat(trn.callerRef!!.callerId).isNotNull
            assertThat(trn.callerRef!!.callerId == i1--.toString())
            assertThat(trn.asset).isNotNull
            assertThat(trn.id).isNotNull
        }
        return sortedResponse
    }

    private fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/assets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(assetRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    AssetUpdateResponse::class.java
                )
        assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }
}