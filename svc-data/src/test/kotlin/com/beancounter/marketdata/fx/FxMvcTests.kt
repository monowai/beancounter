package com.beancounter.marketdata.fx

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbDate
import com.beancounter.marketdata.fx.fxrates.ExRatesResponse
import com.beancounter.marketdata.fx.fxrates.FxGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

/**
 * FX MVC tests for rates at today and historic dates
 */
@SpringBootTest
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureMockMvc
@AutoConfigureMockAuth
internal class FxMvcTests {

    private var dateUtils = DateUtils()

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var currencyService: CurrencyService

    @MockBean
    private lateinit var fxGateway: FxGateway

    @Test
    fun fxResponseObjectReturned() {
        val date = "2019-08-27"
        `when`(fxGateway.getRatesForSymbols(eq(date), eq("USD"), eq(currencyService.currenciesAs)))
            .thenReturn(
                ExRatesResponse(
                    "USD",
                    LocalDate.now(),
                    getFxRates()
                )
            )
        val fxRequest = FxRequest(
            rateDate = date,
            pairs = arrayListOf(
                nzdUsd,
                usdNzd,
                IsoCurrencyPair(usd, usd),
                IsoCurrencyPair(nzd, nzd)
            )
        )

        val mvcResult = fxPost(fxRequest)
        val (results) = BcJson().objectMapper
            .readValue(mvcResult.response.contentAsString, FxResponse::class.java)
        assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size)

        val theRates = results.rates
        assertThat(theRates)
            .containsKeys(nzdUsd, usdNzd)
        for (isoCurrencyPair in theRates.keys) {
            assertThat((results.rates[isoCurrencyPair] ?: error("Date Not Set")).date).isNotNull
        }
    }

    @Test
    fun is_NullDateReturningCurrent() {
        val fxRequest = FxRequest(pairs = arrayListOf(nzdUsd))
        `when`(fxGateway.getRatesForSymbols(any(), eq("USD"), eq(currencyService.currenciesAs)))
            .thenReturn(
                ExRatesResponse(
                    "USD",
                    LocalDate.now(),
                    getFxRates()
                )
            )
        val mvcResult = fxPost(fxRequest)
        val (results) = BcJson().objectMapper
            .readValue(mvcResult.response.contentAsString, FxResponse::class.java)
        assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size)
        val theRates = results.rates
        assertThat(theRates)
            .containsKeys(nzdUsd)
        for (isoCurrencyPair in theRates.keys) {
            assertThat((results.rates[isoCurrencyPair] ?: error("Whoops")).date).isNotNull
        }
    }

    private fun getFxRates(): Map<String, BigDecimal> {
        val ecbResponse = mutableMapOf<String, BigDecimal>()
        currencyService.currencies.forEach { currency ->
            ecbResponse[currency.code] = BigDecimal.ONE
        }
        return ecbResponse
    }

    private fun fxPost(fxRequest: FxRequest, expectedResult: ResultMatcher = status().isOk) =
        mockMvc.perform(
            MockMvcRequestBuilders.post(fxRoot)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(mockAuthConfig.getUserToken(Constants.systemUser))
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    BcJson().objectMapper.writeValueAsString(fxRequest)
                )
        ).andExpect(expectedResult)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    @Test
    fun earliestRateDateValid() {
        val ecbDate = EcbDate(dateUtils)
        assertThat(ecbDate.getValidDate("1990-01-01"))
            .isEqualTo(EcbDate.earliest)
    }

    @Test
    fun invalidCurrenciesThrowError() {
        val date = "2019-08-27"
        val from = "ANC"
        val to = "SDF"
        val invalid = IsoCurrencyPair(from, to)
        val fxRequest = FxRequest(date)
        fxRequest.add(invalid)

        val mvcResult = fxPost(fxRequest, status().is4xxClientError)
        val someException = java.util.Optional.ofNullable(mvcResult.resolvedException as BusinessException)
        assertThat(someException.isPresent).isTrue
        assertThat(someException.get()).hasMessageContaining(from)
    }

    companion object {
        val nzd = NZD.code
        val usd = USD.code
        const val fxRoot = "/fx"
        val usdNzd = IsoCurrencyPair(usd, nzd)
        val nzdUsd = IsoCurrencyPair(nzd, usd)
        const val fxMock = "/mock/fx"
    }
}
