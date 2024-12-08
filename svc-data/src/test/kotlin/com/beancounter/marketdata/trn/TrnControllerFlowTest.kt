package com.beancounter.marketdata.trn

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
import com.beancounter.common.model.Portfolio
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
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.TRADE_DATE
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.TRNS_ROOT
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.URI_TRN_FOR_PORTFOLIO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Splits out the general flow of transactions to verify they work as expected through the trn lifecycle.
 */
@SpringMvcDbTest
class TrnControllerFlowTest(
    @Autowired var mockMvc: MockMvc,
    @Autowired var mockAuthConfig: MockAuthConfig,
) {
    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @MockBean
    private lateinit var figiProxy: FigiProxy

    @MockBean
    private lateinit var fxTransactions: FxTransactions

    private lateinit var token: Jwt
    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var msft: Asset
    private lateinit var aapl: Asset
    private val trnById = "$TRNS_ROOT/{trnId}"
    val trnByPortfolioAndPk = "$TRNS_ROOT/{portfolioId}/{trnId}"
    private lateinit var trnInputA: TrnInput
    private lateinit var trnInputB: TrnInput
    private lateinit var trnInputC: TrnInput
    private lateinit var trnInputD: TrnInput

    @Autowired
    fun setupObjects(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig,
    ) {
        enrichmentFactory.register(DefaultEnricher())
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                token,
            )
        bcMvcHelper.registerUser()
        msft =
            asset(
                AssetRequest(
                    msftInput,
                    MSFT.code,
                ),
            )
        assertThat(msft.id).isNotNull
        aapl =
            asset(
                AssetRequest(
                    aaplInput,
                    AAPL.code,
                ),
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
            )
    }

    @Test
    fun nonExistentThrowsException() {
        // Delete illegal transaction by primary key
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete(
                        trnById,
                        "illegalId",
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
            ).andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
            )
    }

    @Test
    fun is_PersistRetrieveAndPurge() {
        createTestTransactions()
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "Twix",
                    "Name",
                    currency = NZD.code,
                ),
            )
        val trnRequest =
            TrnRequest(
                portfolio.id,
                arrayOf(
                    trnInputA,
                    trnInputB,
                    trnInputC,
                    trnInputD,
                ),
            )
        val trnResponse: TrnResponse =
            objectMapper.readValue(
                bcMvcHelper.postTrn(trnRequest).response.contentAsString,
                TrnResponse::class.java,
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
                                            assetId = msft.id,
                                        ),
                                    ),
                                ).with(
                                    SecurityMockMvcRequestPostProcessors.jwt().jwt(bcMvcHelper.token),
                                ),
                        ).andExpect(MockMvcResultMatchers.status().isOk)
                        .andExpect(
                            MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON),
                        ).andReturn()
                        .response.contentAsString,
                    TrnResponse::class.java,
                )

        assertThat(queryResponse.data).isNotEmpty.hasSize(2) // 2 MSFT transactions
        val mvcResult = findSortedByAssetAndTradeDate(portfolio)
        val firstId =
            checkResponseSortOrder(
                mvcResult,
                trnRequest.data.size,
            ).data.iterator().next().id

        // Find by PrimaryKey
        verifyFoundByPk(
            portfolio,
            firstId,
        )
        val allTrades =
            findTrades(
                portfolio,
                msft,
                token,
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
                token,
            ).data.iterator().next().id,
        ).isEqualTo(id)

        // Delete all remaining transactions for the Portfolio
        assertThat(
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete(
                            "$TRNS_ROOT/portfolio/{portfolioId}",
                            portfolio.id,
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .response.contentAsString,
        ).isNotNull.isEqualTo("3")
    }

    private fun deleteTransaction(
        id: String,
        token: Jwt,
    ): TrnResponse =
        objectMapper.readValue(
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete(
                            trnById,
                            id,
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .response.contentAsByteArray,
            TrnResponse::class.java,
        )

    private fun findSortedByAssetAndTradeDate(portfolio: Portfolio): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get(
                        URI_TRN_FOR_PORTFOLIO,
                        portfolio.id,
                        dateUtils.today(),
                    ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    private fun verifyFoundByPk(
        portfolio: Portfolio,
        id: String,
    ) {
        val pkResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            trnByPortfolioAndPk,
                            portfolio.id,
                            id,
                        ).contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        assertThat(
            objectMapper
                .readValue(
                    pkResult.response.contentAsString,
                    TrnResponse::class.java,
                ).data,
        ).isNotEmpty.hasSize(1)
    }

    private fun findTrades(
        portfolio: Portfolio,
        asset: Asset,
        token: Jwt,
    ): TrnResponse {
        val findByAsset =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$TRNS_ROOT/{portfolioId}/asset/{assetId}/trades",
                            portfolio.id,
                            asset.id,
                        ).contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        return objectMapper
            .readValue(
                findByAsset.response.contentAsString,
                TrnResponse::class.java,
            )
    }

    private fun checkResponseSortOrder(
        mvcResult: MvcResult,
        i: Int,
    ): TrnResponse {
        var i1 = i
        val sortedResponse =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    TrnResponse::class.java,
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
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    AssetUpdateResponse::class.java,
                )
        assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }
}
