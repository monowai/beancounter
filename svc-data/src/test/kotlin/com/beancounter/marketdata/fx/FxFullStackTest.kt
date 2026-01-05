package com.beancounter.marketdata.fx

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.EcbDate
import com.beancounter.marketdata.fx.fxrates.ExRatesResponse
import com.beancounter.marketdata.fx.fxrates.FxGateway
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
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
@SpringMvcDbTest
internal class FxFullStackTest {
    private var dateUtils = DateUtils()

    @MockitoBean
    private lateinit var fxGateway: FxGateway

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var currencyService: CurrencyService

    private val date = "2019-08-27"

    @Test
    fun `should return FX response object with all currency pairs`() {
        mockProviderRates() // Assuming this sets up necessary mock responses for rate requests

        val fxRequest =
            FxRequest(
                rateDate = date,
                pairs =
                    mutableSetOf(
                        nzdUsd,
                        usdNzd,
                        IsoCurrencyPair(
                            usd,
                            usd
                        ),
                        IsoCurrencyPair(
                            nzd,
                            nzd
                        )
                    )
            )

        val results = getResults(fxRequest)
        val theRates = results.rates

        // Assert that the necessary currency pairs are present in the result
        assertThat(theRates.keys).containsExactlyInAnyOrder(
            nzdUsd,
            usdNzd,
            IsoCurrencyPair(
                usd,
                usd
            ),
            IsoCurrencyPair(
                nzd,
                nzd
            )
        )

        // Assert that each currency pair has a non-null date associated with its rate
        for (isoCurrencyPair in theRates.keys) {
            assertThat((results.rates[isoCurrencyPair] ?: error("Date Not Set")).date).isNotNull
        }
    }

    private fun getResults(fxRequest: FxRequest): FxPairResults {
        val mvcResult = fxPost(fxRequest)
        val (results) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    FxResponse::class.java
                )
        assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size)
        return results
    }

    private fun mockProviderRates() {
        `when`(
            fxGateway.getRatesForSymbols(
                eq(date),
                eq(USD.code),
                eq(currencyService.currenciesAs())
            )
        ).thenReturn(
            ExRatesResponse(
                USD.code,
                LocalDate.now(),
                getFxRates()
            )
        )
    }

    @Test
    fun `should return current date when null date is provided`() {
        val fxRequest = FxRequest(pairs = mutableSetOf(nzdUsd))
        `when`(
            fxGateway.getRatesForSymbols(
                any(),
                eq(usd),
                eq(currencyService.currenciesAs())
            )
        ).thenReturn(
            ExRatesResponse(
                usd,
                LocalDate.now(),
                getFxRates()
            )
        )
        val mvcResult = fxPost(fxRequest)
        val (results) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    FxResponse::class.java
                )
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
        currencyService.currencies().forEach { currency ->
            ecbResponse[currency.code] = BigDecimal.ONE
        }
        return ecbResponse
    }

    private fun fxPost(
        fxRequest: FxRequest,
        expectedResult: ResultMatcher = status().isOk,
        mediaType: MediaType = MediaType.APPLICATION_JSON
    ) = mockMvc
        .perform(
            MockMvcRequestBuilders
                .post(FX_ROOT)
                .with(
                    SecurityMockMvcRequestPostProcessors
                        .jwt()
                        .jwt(mockAuthConfig.getUserToken(Constants.systemUser))
                ).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(fxRequest)
                )
        ).andExpect(expectedResult)
        .andExpect(MockMvcResultMatchers.content().contentType(mediaType))
        .andReturn()

    @Test
    fun `should retrieve rates successfully`() {
        val testDate = "2019-07-26"
        `when`(
            fxGateway.getRatesForSymbols(
                eq(testDate),
                eq(USD.code),
                eq(currencyService.currenciesAs())
            )
        ).thenReturn(
            ExRatesResponse(
                base = USD.code,
                date = dateUtils.getDate(testDate),
                mapOf(
                    NZD.code to BigDecimal("1.5"),
                    AUD.code to BigDecimal("1.2"),
                    SGD.code to BigDecimal("1.3")
                )
            )
        )
        val fxRequest =
            FxRequest(
                testDate,
                pairs =
                    mutableSetOf(
                        IsoCurrencyPair(
                            USD.code,
                            NZD.code
                        ),
                        IsoCurrencyPair(
                            USD.code,
                            AUD.code
                        ),
                        IsoCurrencyPair(
                            USD.code,
                            SGD.code
                        )
                    )
            )
        val mvcResult = fxPost(fxRequest)
        val (results) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    FxResponse::class.java
                )
        assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size) // 3 in the request, 3 in the response
    }

    @Test
    fun `should validate earliest rate date`() {
        val ecbDate = EcbDate(dateUtils)
        assertThat(ecbDate.getValidDate("1990-01-01"))
            .isEqualTo(EcbDate.EARLIEST)
    }

    @Test
    fun `should throw error for invalid currencies`() {
        val date = "2019-08-27"
        val from = "ANC"
        val to = "SDF"
        val invalid =
            IsoCurrencyPair(
                from,
                to
            )
        val fxRequest = FxRequest(date)
        fxRequest.add(invalid)
        val mvcResult =
            fxPost(
                fxRequest,
                status().is4xxClientError,
                MediaType.APPLICATION_PROBLEM_JSON
            )
        val someException =
            java.util.Optional.ofNullable(
                mvcResult.resolvedException as BusinessException
            )
        assertThat(someException.isPresent).isTrue
        assertThat(someException.get()).hasMessageContaining(from)
    }

    @Autowired
    private lateinit var fxRateRepository: FxRateRepository

    @Test
    fun `should fetch rates even when multiple base rates exist from different providers`() {
        // Given: Multiple base rates from different providers exist in the database
        val baseCurrency = currencyService.getCode(USD.code)
        val baseDate = dateUtils.getDate("1900-01-01")

        // Create base rates from multiple providers (simulating production scenario)
        val frankfurterBase =
            com.beancounter.common.model.FxRate(
                from = baseCurrency,
                to = baseCurrency,
                rate = BigDecimal.ONE,
                date = baseDate,
                provider = "FRANKFURTER"
            )
        val exchangeRatesBase =
            com.beancounter.common.model.FxRate(
                from = baseCurrency,
                to = baseCurrency,
                rate = BigDecimal.ONE,
                date = baseDate,
                provider = "EXCHANGE_RATES_API"
            )
        fxRateRepository.save(frankfurterBase)
        fxRateRepository.save(exchangeRatesBase)

        // And: Mock the API to return actual rates
        val testDate = "2019-07-26"
        `when`(
            fxGateway.getRatesForSymbols(
                eq(testDate),
                eq(USD.code),
                eq(currencyService.currenciesAs())
            )
        ).thenReturn(
            ExRatesResponse(
                base = USD.code,
                date = dateUtils.getDate(testDate),
                mapOf(
                    NZD.code to BigDecimal("1.5"),
                    AUD.code to BigDecimal("1.2"),
                    SGD.code to BigDecimal("1.3")
                )
            )
        )

        // When: Requesting FX rates for a date that's not cached
        val fxRequest =
            FxRequest(
                testDate,
                pairs =
                    mutableSetOf(
                        IsoCurrencyPair(USD.code, NZD.code),
                        IsoCurrencyPair(USD.code, AUD.code),
                        IsoCurrencyPair(AUD.code, SGD.code)
                    )
            )
        val mvcResult = fxPost(fxRequest)

        // Then: Rates should be fetched from API and returned
        val (results) =
            objectMapper.readValue(
                mvcResult.response.contentAsString,
                FxResponse::class.java
            )
        assertThat(results.rates)
            .isNotNull
            .hasSize(3)

        // Verify cross-rate calculation works (AUD:SGD)
        val audSgdRate = results.rates[IsoCurrencyPair(AUD.code, SGD.code)]
        assertThat(audSgdRate).isNotNull
        assertThat(audSgdRate!!.rate).isNotNull
    }

    companion object {
        val nzd = NZD.code
        val usd = USD.code
        const val FX_ROOT = "/fx"
        val usdNzd =
            IsoCurrencyPair(
                usd,
                nzd
            )
        val nzdUsd =
            IsoCurrencyPair(
                nzd,
                usd
            )
        const val FX_MOCK = "/mock/fx"
    }
}