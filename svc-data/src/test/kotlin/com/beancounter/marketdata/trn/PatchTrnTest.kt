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
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal

@SpringMvcDbTest
class PatchTrnTest {
    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var aapl: Asset
    private val dateUtils = DateUtils()

    @MockBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var currencyService: CurrencyService

    private lateinit var token: Jwt

    @Autowired
    fun setupObjects(
        mockMvc: MockMvc,
        mockAuthConfig: MockAuthConfig,
    ) {
        assertThat(currencyService.currencies).isNotEmpty

        token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        RegistrationUtils.registerUser(mockMvc, token)
        aapl =
            bcMvcHelper.asset(
                AssetRequest(Constants.aaplInput),
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
                    currency = Constants.NZD.code,
                ),
            )
        val originalTransaction = createAndPostTransaction(portfolio)
        assertThat(originalTransaction.data).hasSize(1)
        // Patch the transaction
        val patchedTransaction =
            patchTransaction(portfolio.id, originalTransaction.data.iterator().next().id)

        // Validate that the transaction was patched correctly
        validatePatchedTransaction(portfolio, originalTransaction, patchedTransaction)
    }

    private fun createAndPostTransaction(
        portfolio: Portfolio,
        tradeDate: String = "2020-03-10",
    ): TrnResponse {
        val trnInput =
            TrnInput(
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getDate(tradeDate),
                tradePortfolioRate = BigDecimal.TEN,
                comments = "The Comments Will Not Change",
            )
        val transactionRequest = TrnRequest(portfolio.id, arrayOf(trnInput))
        val httpResponse = bcMvcHelper.postTrn(transactionRequest).response.contentAsString
        return objectMapper.readValue(httpResponse, TrnResponse::class.java)
    }

    private fun patchTransaction(
        portfolioId: String,
        transactionId: String,
        newTradeDate: String = "2021-03-10",
    ): TrnResponse {
        val updatedTrnInput =
            TrnInput(
                assetId = aapl.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal.TEN,
                tradeCurrency = USD.code,
                tradeDate = dateUtils.getDate(newTradeDate),
                price = BigDecimal.TEN,
                tradePortfolioRate = BigDecimal.ONE,
            )
        val httpResponse = bcMvcHelper.patchTrn(portfolioId, transactionId, updatedTrnInput).response.contentAsString
        return objectMapper.readValue(httpResponse, TrnResponse::class.java)
    }

    private fun validatePatchedTransaction(
        portfolio: Portfolio,
        originalTransaction: TrnResponse,
        patchedTransaction: TrnResponse,
    ) {
        assertThat(patchedTransaction.data.size).isEqualTo(1)
        val updatedTrn =
            objectMapper.readValue(
                bcMvcHelper.getTrnById(
                    portfolio.id,
                    originalTransaction.data.iterator().next().id,
                ).response.contentAsString,
                TrnResponse::class.java,
            ).data.iterator().next()

        assertThat(updatedTrn).isNotNull
        assertThat(updatedTrn).hasFieldOrPropertyWithValue("id", originalTransaction.data.iterator().next().id)
        assertThat(updatedTrn).hasFieldOrPropertyWithValue(
            "comments",
            originalTransaction.data.iterator().next().comments,
        )
        assertThat(updatedTrn).hasFieldOrPropertyWithValue("tradePortfolioRate", BigDecimal("1.000000"))
        assertThat(updatedTrn).hasFieldOrPropertyWithValue("price", BigDecimal("10.000000"))
        assertThat(updatedTrn).hasFieldOrPropertyWithValue("tradeDate", dateUtils.getDate("2021-03-10"))
    }
}
