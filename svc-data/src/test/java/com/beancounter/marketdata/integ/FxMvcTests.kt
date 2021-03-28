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
import com.beancounter.marketdata.providers.fxrates.EcbDate
import com.beancounter.marketdata.utils.AlphaMockUtils
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Optional

@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
@EntityScan("com.beancounter.common.model")
internal class FxMvcTests {
    private val authorityRoleConverter = AuthorityRoleConverter()

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var context: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    companion object {
        val api = AlphaMockUtils.getAlphaApi()

        @BeforeAll
        @JvmStatic
        fun is_ApiRunning() {
            Assertions.assertThat(api).isNotNull
            Assertions.assertThat(api.isRunning).isTrue
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
    fun is_FxResponseObjectReturned() {
        val rateResponse = ClassPathResource("contracts/ecb/fx-current-rates.json").file
        AlphaMockUtils.mockGetResponse(
            "/2019-08-27?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD", // Matches all supported currencies
            rateResponse
        )
        val date = "2019-08-27"
        val nzdUsd = IsoCurrencyPair("NZD", "USD")
        val usdNzd = IsoCurrencyPair("USD", "NZD")
        val usdUsd = IsoCurrencyPair("USD", "USD")
        val nzdNzd = IsoCurrencyPair("NZD", "NZD")
        val fxRequest = FxRequest(date)
            .add(nzdUsd).add(usdNzd).add(usdUsd).add(nzdNzd)

        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/fx")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    BcJson().objectMapper.writeValueAsString(fxRequest)
                )
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (results) = BcJson().objectMapper
            .readValue(mvcResult.response.contentAsString, FxResponse::class.java)
        Assertions.assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size)

        val theRates = results.rates
        Assertions.assertThat(theRates)
            .containsKeys(nzdUsd, usdNzd)
        for (isoCurrencyPair in theRates.keys) {
            Assertions.assertThat((results.rates[isoCurrencyPair] ?: error("Date Not Set")).date).isNotNull
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_NullDateReturningCurrent() {
        val rateResponse = ClassPathResource("contracts/ecb/fx-current-rates.json").file
        val today = dateUtils.today()
        AlphaMockUtils.mockGetResponse(
            "/$today?base=USD&symbols=AUD%2CEUR%2CGBP%2CNZD%2CSGD%2CUSD", // Matches all supported currencies
            rateResponse
        )
        val nzdUsd = IsoCurrencyPair("USD", "NZD")
        val fxRequest = FxRequest()
        fxRequest.add(nzdUsd)
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/fx")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(BcJson().objectMapper.writeValueAsString(fxRequest))
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (results) = BcJson().objectMapper
            .readValue(mvcResult.response.contentAsString, FxResponse::class.java)
        Assertions.assertThat(results.rates)
            .isNotNull
            .hasSize(fxRequest.pairs.size)
        val theRates = results.rates
        Assertions.assertThat(theRates)
            .containsKeys(nzdUsd)
        for (isoCurrencyPair in theRates.keys) {
            Assertions.assertThat((results.rates[isoCurrencyPair] ?: error("Whoops")).date).isNotNull
        }
    }

    @Test
    fun is_EarliestRateDateValid() {
        val ecbDate = EcbDate(dateUtils)
        Assertions.assertThat(ecbDate.getValidDate("1990-01-01"))
            .isEqualTo(EcbDate.earliest)
    }

    @Test
    @Throws(Exception::class)
    fun is_InvalidCurrenciesReturned() {
        val date = "2019-08-27"
        val invalid = IsoCurrencyPair("ANC", "SDF")
        val fxRequest = FxRequest(date)
        fxRequest.add(invalid)
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/fx")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    BcJson().objectMapper.writeValueAsString(fxRequest)
                )
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
        val someException = Optional.ofNullable(mvcResult.resolvedException as BusinessException)
        Assertions.assertThat(someException.isPresent).isTrue
        Assertions.assertThat(someException.get()).hasMessageContaining("ANC").hasMessageContaining("SDF")
    }
}
