package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.providers.fxrates.EcbDate
import com.beancounter.marketdata.utils.RegistrationUtils
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

/**
 * FX MVC tests for rates at today and historic dates
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
@AutoConfigureWireMock(port = 0)
internal class FxMvcTests {
    private val authorityRoleConverter = AuthorityRoleConverter()

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var context: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    companion object {
        val nzd = NZD.code
        val usd = USD.code
        const val fxRoot = "/fx"
        val usdNzd = IsoCurrencyPair(usd, nzd)
        val nzdUsd = IsoCurrencyPair(nzd, usd)
        const val fxMock = "/mock/fx"

        @BeforeAll
        @JvmStatic
        fun mockResponse() {
            val rateResponse = ClassPathResource("$fxMock/fx-current-rates.json").file
            val today = EcbDate(dateUtils = DateUtils()).getValidDate("today")

            stubFx(
                // Matches all supported currencies
                "/v1/2019-08-27?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD&access_key=test",
                rateResponse
            )
            stubFx(
                "/v1/$today?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD&access_key=test",
                rateResponse
            )
        }

        @JvmStatic
        fun stubFx(url: String, rateResponse: File) {
            WireMock.stubFor(
                WireMock.get(WireMock.urlEqualTo(url))
                    .willReturn(
                        WireMock.aResponse()
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody(
                                BcJson().objectMapper.writeValueAsString(
                                    BcJson().objectMapper.readValue(
                                        rateResponse,
                                        HashMap::class.java
                                    )
                                )
                            )
                            .withStatus(200)
                    )
            )
        }
    }

    @Autowired
    fun mockServices() {

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
        val user = SystemUser("user", "user@testing.com")
        token = TokenUtils().getUserToken(user)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    @Throws(Exception::class)
    fun fxResponseObjectReturned() {
        val date = "2019-08-27"
        val fxRequest = FxRequest(
            rateDate = date,
            pairs = arrayListOf(
                nzdUsd,
                usdNzd,
                IsoCurrencyPair(usd, usd),
                IsoCurrencyPair(nzd, nzd),
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
    @Throws(Exception::class)
    fun is_NullDateReturningCurrent() {
        val fxRequest = FxRequest(pairs = arrayListOf(nzdUsd))
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

    private fun fxPost(fxRequest: FxRequest, expectedResult: ResultMatcher = MockMvcResultMatchers.status().isOk) =
        mockMvc.perform(
            MockMvcRequestBuilders.post(fxRoot)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
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
    @Throws(Exception::class)
    fun invalidCurrenciesReturned() {
        val date = "2019-08-27"
        val from = "ANC"
        val to = "SDF"
        val invalid = IsoCurrencyPair(from, to)
        val fxRequest = FxRequest(date)
        fxRequest.add(invalid)

        val mvcResult = fxPost(fxRequest, MockMvcResultMatchers.status().is4xxClientError)
        val someException = java.util.Optional.ofNullable(mvcResult.resolvedException as BusinessException)
        assertThat(someException.isPresent).isTrue
        assertThat(someException.get()).hasMessageContaining(from).hasMessageContaining(to)
    }
}
