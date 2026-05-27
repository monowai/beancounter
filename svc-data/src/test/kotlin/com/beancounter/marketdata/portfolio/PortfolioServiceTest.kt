package com.beancounter.marketdata.portfolio

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.TokenService
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@SpringMvcDbTest
@AutoConfigureMockAuth
class PortfolioServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var systemUserService: SystemUserService

    @Test
    fun maintainPortfolioForServiceAccount() {
        Mockito.`when`(tokenService.isServiceToken).thenReturn(true)
        assertThat(systemUserService.registerSystemAccount("beancounter:system")).isNotNull

        val results =
            portfolioService.save(
                listOf(
                    PortfolioInput(
                        "TEST",
                        "Test Portfolio",
                        "USD",
                        currency = "USD"
                    )
                )
            )
        assertThat(results).hasSize(1)
        val portfolio = results.iterator().next()
        assertThat(portfolio)
            .hasFieldOrPropertyWithValue(
                "code",
                "TEST"
            ).hasFieldOrPropertyWithValue(
                "marketValue",
                BigDecimal.ZERO
            ).hasFieldOrPropertyWithValue(
                "irr",
                BigDecimal.ZERO
            )
        val result =
            portfolioService.maintain(
                // Update props. Privileged action!
                Portfolio(
                    portfolio.id,
                    "TEST",
                    "Test Portfolio",
                    marketValue = BigDecimal.TEN,
                    irr = BigDecimal.ONE,
                    owner = portfolio.owner
                )
            )
        assertThat(result)
            .hasFieldOrPropertyWithValue(
                "marketValue",
                BigDecimal.TEN
            ).hasFieldOrPropertyWithValue(
                "irr",
                BigDecimal.ONE
            )
    }

    @Test
    fun `update preserves valuation fields and applies cashPortfolioId`() {
        Mockito.`when`(tokenService.isServiceToken).thenReturn(true)
        assertThat(systemUserService.registerSystemAccount("beancounter:system")).isNotNull

        val created =
            portfolioService
                .save(
                    listOf(
                        PortfolioInput(
                            code = "UPDP",
                            name = "Update Portfolio",
                            currency = "USD",
                            base = "USD"
                        )
                    )
                ).iterator()
                .next()

        // Simulate bc-position stream having written valuation back to the DB.
        portfolioService.maintain(
            created.copy(
                marketValue = BigDecimal("1234.56"),
                irr = BigDecimal("0.05"),
                gainOnDay = BigDecimal("12.34"),
                valuedAt = java.time.LocalDate.of(2026, 5, 27)
            )
        )

        // Now user edits the portfolio and assigns a cash funding portfolio.
        val updated =
            portfolioService.update(
                created.id,
                PortfolioInput(
                    code = "UPDP",
                    name = "Update Portfolio",
                    currency = "USD",
                    base = "USD",
                    cashPortfolioId = "FUNDING-1"
                )
            )

        assertThat(updated.cashPortfolioId).isEqualTo("FUNDING-1")
        assertThat(updated.marketValue).isEqualByComparingTo(BigDecimal("1234.56"))
        assertThat(updated.irr).isEqualByComparingTo(BigDecimal("0.05"))
        assertThat(updated.gainOnDay).isEqualByComparingTo(BigDecimal("12.34"))
        assertThat(updated.valuedAt).isEqualTo(java.time.LocalDate.of(2026, 5, 27))
    }
}