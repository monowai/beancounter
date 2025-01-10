package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

/**
 * Ensure patching behaviour works as expected.
 */
@SpringMvcDbTest
class PatchTrnTest {
    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var aapl: Asset
    private val dateUtils = DateUtils()

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    @BeforeEach
    fun configure() {
        assertThat(currencyService.currencies()).isNotEmpty

        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                token
            )

        assertThat(fxTransactions).isNotNull
        RegistrationUtils.registerUser(
            mockMvc,
            token
        )

        aapl =
            bcMvcHelper.asset(
                AssetRequest(Constants.aaplInput)
            )
    }

    @Test
    fun `verify transaction patching updates data correctly`() {
        // Setup the initial portfolio and transaction
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    "PATCH",
                    "is_TrnPatched",
                    currency = Constants.NZD.code
                )
            )
        val originalTransaction = createAndPostTransaction(portfolio)
        assertThat(originalTransaction.data).hasSize(1)
        // Patch the transaction
        val patchedTransaction =
            patchTransaction(
                portfolio.id,
                originalTransaction.data
                    .iterator()
                    .next()
                    .id
            )

        // Validate that the transaction was patched correctly
        validatePatchedTransaction(
            originalTransaction,
            patchedTransaction
        )
    }

    private fun createAndPostTransaction(
        portfolio: Portfolio,
        tradeDate: String = "2020-03-10"
    ): TrnResponse {
        val trnInput =
            TrnInput(
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate(tradeDate),
                tradePortfolioRate = BigDecimal.TEN,
                comments = "The Comments Will Not Change"
            )
        val transactionRequest =
            TrnRequest(
                portfolio.id,
                arrayOf(trnInput)
            )
        val httpResponse = bcMvcHelper.postTrn(transactionRequest).response.contentAsString
        return objectMapper.readValue(
            httpResponse,
            TrnResponse::class.java
        )
    }

    private fun patchTransaction(
        portfolioId: String,
        transactionId: String,
        newTradeDate: String = "2021-03-10"
    ): TrnResponse {
        val updatedTrnInput =
            TrnInput(
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getFormattedDate(newTradeDate),
                price = BigDecimal.TEN,
                tradePortfolioRate = BigDecimal.ONE
            )
        val httpResponse =
            bcMvcHelper
                .patchTrn(
                    portfolioId,
                    transactionId,
                    updatedTrnInput
                ).response.contentAsString
        return objectMapper.readValue(
            httpResponse,
            TrnResponse::class.java
        )
    }

    private fun validatePatchedTransaction(
        originalTransaction: TrnResponse,
        patchedTransaction: TrnResponse
    ) {
        assertThat(patchedTransaction.data.size).isEqualTo(1)
        val updatedTrn =
            objectMapper
                .readValue(
                    bcMvcHelper
                        .getTrnById(
                            originalTransaction.data
                                .iterator()
                                .next()
                                .id
                        ).response.contentAsString,
                    TrnResponse::class.java
                ).data
                .iterator()
                .next()

        assertThat(updatedTrn)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "id",
                originalTransaction.data
                    .iterator()
                    .next()
                    .id
            ).hasFieldOrPropertyWithValue(
                "comments",
                originalTransaction.data
                    .iterator()
                    .next()
                    .comments
            ).hasFieldOrPropertyWithValue(
                "tradePortfolioRate",
                BigDecimal("1.000000")
            ).hasFieldOrPropertyWithValue(
                "price",
                BigDecimal("10.000000")
            ).hasFieldOrPropertyWithValue(
                "tradeDate",
                dateUtils.getFormattedDate("2021-03-10")
            )
    }
}