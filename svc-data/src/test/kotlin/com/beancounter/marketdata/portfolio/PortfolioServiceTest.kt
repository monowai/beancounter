package com.beancounter.marketdata.portfolio

import com.beancounter.auth.TokenService
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.math.BigDecimal

@SpringBootTest
class PortfolioServiceTest {
    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var systemUserService: SystemUserService

    @MockBean
    private lateinit var tokenService: TokenService

    @Test
    fun maintainPortfolioForServiceAccount() {
        Mockito.`when`(tokenService.isServiceToken).thenReturn(true)
        assertThat(systemUserService.registerSystemAccount("beancounter:system")).isNotNull

        val results = portfolioService.save(listOf(PortfolioInput("TEST", "Test Portfolio", "USD", currency = "USD")))
        assertThat(results).hasSize(1)
        val portfolio = results.iterator().next()
        assertThat(portfolio)
            .hasFieldOrPropertyWithValue("code", "TEST")
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal.ZERO)
            .hasFieldOrPropertyWithValue("irr", BigDecimal.ZERO)
        val result =
            portfolioService.maintain(
                // Update props. Privileged action!
                Portfolio(
                    portfolio.id,
                    "TEST",
                    "Test Portfolio",
                    marketValue = BigDecimal.TEN,
                    irr = BigDecimal.ONE,
                    owner = portfolio.owner,
                ),
            )
        assertThat(result)
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal.TEN)
            .hasFieldOrPropertyWithValue("irr", BigDecimal.ONE)
    }
}
