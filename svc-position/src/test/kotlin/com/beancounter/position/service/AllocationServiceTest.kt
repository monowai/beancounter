package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Totals
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for [AllocationService.convertPositionsToCurrency].
 */
@ExtendWith(MockitoExtension::class)
class AllocationServiceTest {
    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var fxService: FxService

    private lateinit var allocationService: AllocationService

    @BeforeEach
    fun setUp() {
        allocationService = AllocationService(tokenService, fxService)
    }

    @Test
    fun `convertPositionsToCurrency applies FX rate to PORTFOLIO view`() {
        val usd = Currency("USD")
        val sgd = Currency("SGD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        val portfolioValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val baseValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val tradeValues = MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        position.moneyValues[Position.In.PORTFOLIO] = portfolioValues
        position.moneyValues[Position.In.BASE] = baseValues
        position.moneyValues[Position.In.TRADE] = tradeValues

        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        val pair = IsoCurrencyPair(from = "USD", to = "SGD")
        val rate = BigDecimal("1.35")
        whenever(tokenService.bearerToken).thenReturn("bearer")
        whenever(fxService.getRates(any<FxRequest>(), eq("bearer"))).thenReturn(
            FxResponse(
                FxPairResults(rates = mapOf(pair to FxRate(from = usd, to = sgd, rate = rate)))
            )
        )

        val result = allocationService.convertPositionsToCurrency(positions, "SGD", "2024-01-15")

        val converted =
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
        assertThat(converted.currency.code).isEqualTo("SGD")
        assertThat(converted.marketValue).isEqualByComparingTo("1350")
        // TRADE and BASE views should not be touched
        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.BASE]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.TRADE]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.currency.code).isEqualTo("SGD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.marketValue).isEqualByComparingTo("1350")
    }

    @Test
    fun `convertPositionsToCurrency is a no-op when source equals target`() {
        val usd = Currency("USD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        val result = allocationService.convertPositionsToCurrency(positions, "USD", "2024-01-15")

        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
                .marketValue
        ).isEqualByComparingTo("1000")
        verify(fxService, org.mockito.kotlin.never()).getRates(any<FxRequest>(), any())
    }

    @Test
    fun `convertPositionsToCurrency returns positions unchanged when no FX rate resolves`() {
        val usd = Currency("USD")
        val portfolio = TestHelpers.createTestPortfolio(currencyCode = "USD")
        val position = TestHelpers.createTestPosition(portfolio = portfolio)
        position.moneyValues[Position.In.PORTFOLIO] =
            MoneyValues(usd).apply { marketValue = BigDecimal("1000") }
        val positions = TestHelpers.createTestPositions(portfolio, listOf(position))
        positions.totals[Position.In.PORTFOLIO] =
            Totals(currency = usd, marketValue = BigDecimal("1000"))

        whenever(tokenService.bearerToken).thenReturn("bearer")
        whenever(fxService.getRates(any<FxRequest>(), eq("bearer")))
            .thenReturn(FxResponse(FxPairResults(rates = emptyMap())))

        val result = allocationService.convertPositionsToCurrency(positions, "SGD", "2024-01-15")

        assertThat(
            result.positions.values
                .first()
                .moneyValues[Position.In.PORTFOLIO]!!
                .currency.code
        ).isEqualTo("USD")
        assertThat(result.totals[Position.In.PORTFOLIO]!!.marketValue).isEqualByComparingTo("1000")
    }
}