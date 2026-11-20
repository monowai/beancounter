package com.beancounter.marketdata.payslip

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.enrichment.DefaultEnricher
import com.beancounter.marketdata.assets.enrichment.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import java.time.LocalDate

/**
 * The caller's one recurring payslip, end to end through MockMvc against the H2 test database.
 * Each test registers its own users so rows never leak between tests.
 */
@SpringMvcDbTest
class RecurringPayslipControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var recurringPayslipRepository: RecurringPayslipRepository

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
    }

    /** A registered user with a portfolio and an SGD bank account. */
    private inner class Payee(
        name: String
    ) {
        val helper = BcMvcHelper(mockMvc, mockAuthConfig.getUserToken(SystemUser(name, "$name@testing.com")))
        val token: Jwt = helper.token

        init {
            helper.registerUser()
        }

        val portfolio: Portfolio = helper.portfolio(PortfolioInput(name.uppercase(), name, currency = SGD.code))
        val cash: Asset =
            helper.asset(AssetRequest(AssetInput.toAccount(SGD, "$name-BANK", "$name bank", owner = "")))

        fun body(
            dayOfMonth: Int = 25,
            nextPayDate: String = "2026-12-25",
            gross: String = "10000",
            tax: String = "500",
            portfolioId: String = portfolio.id,
            cashAssetId: String = cash.id
        ) = mapOf(
            "portfolioId" to portfolioId,
            "cashAssetId" to cashAssetId,
            "grossSalary" to gross.toBigDecimal(),
            "tax" to tax.toBigDecimal(),
            "dayOfMonth" to dayOfMonth,
            "nextPayDate" to nextPayDate
        )
    }

    private fun call(
        request: MockHttpServletRequestBuilder,
        token: Jwt,
        expect: ResultMatcher = status().isOk,
        body: Any? = null
    ): JsonNode {
        val builder = request.with(jwt().jwt(token)).with(csrf())
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))
        }
        val content =
            mockMvc
                .perform(builder)
                .andExpect(expect)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(content.ifBlank { "{}" })
    }

    private fun get(payee: Payee): JsonNode = call(get(ROOT), payee.token)

    private fun put(
        payee: Payee,
        body: Map<String, Any?> = payee.body(),
        expect: ResultMatcher = status().isOk
    ): JsonNode = call(put(ROOT), payee.token, expect, body)

    @Test
    fun `should answer null data when the user has no recurring payslip`() {
        val payee = Payee("rp-none")

        val response = get(payee)

        assertThat(response.has("data")).isTrue()
        assertThat(response.path("data").isNull).isTrue()
    }

    @Test
    fun `should schedule the next payslip on the given date and day and read it back`() {
        val payee = Payee("rp-roundtrip")

        val saved = put(payee).path("data")

        assertThat(saved.path("id").asString()).isNotBlank()
        assertThat(saved.path("portfolioId").asString()).isEqualTo(payee.portfolio.id)
        assertThat(saved.path("cashAssetId").asString()).isEqualTo(payee.cash.id)
        assertThat(saved.path("grossSalary").decimalValue()).isEqualByComparingTo("10000")
        assertThat(saved.path("tax").decimalValue()).isEqualByComparingTo("500")
        assertThat(saved.path("dayOfMonth").asInt()).isEqualTo(25)
        assertThat(saved.path("nextPayDate").asString()).isEqualTo("2026-12-25")
        assertThat(saved.has("ownerId")).isFalse()

        val read = get(payee).path("data")
        assertThat(read.propertyNames()).containsExactlyInAnyOrderElementsOf(saved.propertyNames())
        listOf("id", "portfolioId", "cashAssetId", "dayOfMonth", "nextPayDate").forEach {
            assertThat(read.path(it)).describedAs(it).isEqualTo(saved.path(it))
        }
        assertThat(read.path("grossSalary").decimalValue()).isEqualByComparingTo("10000")
        assertThat(read.path("tax").decimalValue()).isEqualByComparingTo("500")
        val stored = recurringPayslipRepository.findById(saved.path("id").asString()).orElseThrow()
        assertThat(stored.ownerId).isEqualTo(payee.portfolio.owner.id)
    }

    @Test
    fun `should replace the user's one recurring payslip on a second put`() {
        val payee = Payee("rp-replace")
        val first = put(payee).path("data")

        val second =
            put(
                payee,
                payee.body(dayOfMonth = 10, nextPayDate = "2026-12-10", gross = "12000", tax = "0")
            ).path("data")

        assertThat(second.path("id").asString()).isEqualTo(first.path("id").asString())
        assertThat(second.path("grossSalary").decimalValue()).isEqualByComparingTo("12000")
        assertThat(second.path("tax").decimalValue()).isEqualByComparingTo("0")
        assertThat(second.path("dayOfMonth").asInt()).isEqualTo(10)
        assertThat(second.path("nextPayDate").asString()).isEqualTo("2026-12-10")
        assertThat(recurringPayslipRepository.findByOwnerId(payee.portfolio.owner.id)).isPresent
        assertThat(recurringPayslipRepository.findAll().count { it.ownerId == payee.portfolio.owner.id })
            .isEqualTo(1)
    }

    @Test
    fun `should keep day of month 31 when the next pay date is a shorter month's last day`() {
        val payee = Payee("rp-clamp")

        val nonLeap = put(payee, payee.body(dayOfMonth = 31, nextPayDate = "2027-02-28")).path("data")
        assertThat(nonLeap.path("nextPayDate").asString()).isEqualTo("2027-02-28")
        assertThat(nonLeap.path("dayOfMonth").asInt()).isEqualTo(31)

        val leap = put(payee, payee.body(dayOfMonth = 31, nextPayDate = "2028-02-29")).path("data")
        assertThat(leap.path("nextPayDate").asString()).isEqualTo("2028-02-29")
        assertThat(leap.path("dayOfMonth").asInt()).isEqualTo(31)
    }

    @Test
    fun `should not show another user's recurring payslip`() {
        val owner = Payee("rp-owner")
        val other = Payee("rp-other")
        put(owner)

        assertThat(get(other).path("data").isNull).isTrue()

        call(delete(ROOT), other.token, status().isNoContent)
        assertThat(get(owner).path("data").isNull).isFalse()
    }

    @Test
    fun `should stop the recurring payslip and answer 204 again when already stopped`() {
        val payee = Payee("rp-stop")
        put(payee)

        call(delete(ROOT), payee.token, status().isNoContent)
        call(delete(ROOT), payee.token, status().isNoContent)

        assertThat(get(payee).path("data").isNull).isTrue()
    }

    @Test
    fun `should reject bad amounts and a portfolio or cash asset the caller cannot use`() {
        val payee = Payee("rp-invalid")
        val other = Payee("rp-invalid-other")

        put(payee, payee.body(gross = "0"), status().isBadRequest)
        put(payee, payee.body(gross = "-1"), status().isBadRequest)
        put(payee, payee.body(tax = "-0.01"), status().isBadRequest)
        put(payee, payee.body(portfolioId = other.portfolio.id), status().isBadRequest)
        put(payee, payee.body(portfolioId = "no-such-portfolio"), status().isBadRequest)
        put(payee, payee.body(cashAssetId = "no-such-asset"), status().isBadRequest)
        put(payee, payee.body(cashAssetId = other.cash.id), status().isBadRequest)
        put(payee, payee.body(dayOfMonth = 0, nextPayDate = "2026-12-01"), status().isBadRequest)
        put(payee, payee.body(dayOfMonth = 32, nextPayDate = "2026-12-31"), status().isBadRequest)
        put(payee, payee.body(dayOfMonth = 31, nextPayDate = "2027-03-30"), status().isBadRequest)
        put(payee, payee.body(dayOfMonth = 25, nextPayDate = "2026-12-24"), status().isBadRequest)

        assertThat(get(payee).path("data").isNull).isTrue()
    }

    @Test
    fun `should remove the recurring payslip when its portfolio is deleted`() {
        val payee = Payee("rp-pf-delete")
        put(payee)

        call(delete("/portfolios/{id}", payee.portfolio.id), payee.token)

        assertThat(get(payee).path("data").isNull).isTrue()
        assertThat(recurringPayslipRepository.findByOwnerId(payee.portfolio.owner.id)).isEmpty
    }

    @Test
    fun `should remove the recurring payslip when the user deletes their portfolios while offboarding`() {
        val payee = Payee("rp-offboard")
        put(payee)

        call(delete("/offboard/portfolios"), payee.token)

        assertThat(recurringPayslipRepository.findByOwnerId(payee.portfolio.owner.id)).isEmpty
    }

    companion object {
        private const val ROOT = "/payslips/recurring"
    }
}