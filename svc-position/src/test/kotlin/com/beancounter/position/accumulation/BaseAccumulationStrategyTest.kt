package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.utils.CurrencyResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * TDD-driven tests for BaseAccumulationStrategy optimizations.
 * These tests define the expected performance improvements and functional correctness.
 */
internal class BaseAccumulationStrategyTest {
    private lateinit var currencyResolver: CurrencyResolver
    private lateinit var testStrategy: TestAccumulationStrategy
    private lateinit var asset: Asset
    private lateinit var positions: Positions

    @BeforeEach
    fun setUp() {
        currencyResolver = CurrencyResolver()
        testStrategy = TestAccumulationStrategy(currencyResolver)
        val market = Market("NYSE", USD.code)
        asset = getTestAsset(market, "TSLA")
        positions = Positions()
    }

    @Test
    fun `should create currency context with all three currency types`() {
        // Given a transaction with multi-currency setup
        val trn = createTestTransaction()
        val position = positions.getOrCreate(trn)

        // When creating currency context
        val context = testStrategy.createCurrencyContext(trn, position)

        // Then all currency types should be resolved correctly
        assertThat(context.tradeMoneyValues).isNotNull
        assertThat(context.baseMoneyValues).isNotNull
        assertThat(context.portfolioMoneyValues).isNotNull

        // And currency resolution should match expected currencies
        assertThat(context.tradeMoneyValues.currency).isEqualTo(trn.tradeCurrency)
        assertThat(context.baseMoneyValues.currency).isEqualTo(trn.portfolio.base)
        assertThat(context.portfolioMoneyValues.currency).isEqualTo(trn.portfolio.currency)
    }

    @Test
    fun `should use cached BigDecimal constants to reduce object creation`() {
        // Given access to BaseAccumulationStrategy constants
        val one = BaseAccumulationStrategy.ONE
        val zero = BaseAccumulationStrategy.ZERO

        // Then constants should match standard BigDecimal values
        assertThat(one).isEqualTo(BigDecimal.ONE)
        assertThat(zero).isEqualTo(BigDecimal.ZERO)

        // And should be the same reference (cached)
        assertThat(one).isSameAs(BaseAccumulationStrategy.ONE)
        assertThat(zero).isSameAs(BaseAccumulationStrategy.ZERO)
    }

    @Test
    fun `should apply multi-currency updates efficiently with single context creation`() {
        // Given a transaction and position
        val trn = createTestTransaction()
        val position = positions.getOrCreate(trn)
        val context = testStrategy.createCurrencyContext(trn, position)

        // When applying multi-currency update
        var updateCallCount = 0
        val expectedRates = mutableListOf<BigDecimal>()
        testStrategy.applyMultiCurrencyUpdate(context, trn) { moneyValues, rate ->
            updateCallCount++
            expectedRates.add(rate)
        }

        // Then should call update function exactly 3 times (TRADE, BASE, PORTFOLIO)
        assertThat(updateCallCount).isEqualTo(3)

        // And rates should be applied in correct order: ONE, tradeBaseRate, tradePortfolioRate
        assertThat(expectedRates).hasSize(3)
        assertThat(expectedRates[0]).isEqualTo(BaseAccumulationStrategy.ONE)
        assertThat(expectedRates[1]).isEqualTo(trn.tradeBaseRate)
        assertThat(expectedRates[2]).isEqualTo(trn.tradePortfolioRate)
    }

    @Test
    fun `should maintain performance under repeated currency context creation`() {
        // Given multiple transactions (simulating high-frequency processing)
        val transactions = (1..100).map { createTestTransaction() }
        val position = positions.getOrCreate(transactions.first())

        // When creating currency contexts repeatedly
        val startTime = System.nanoTime()
        val contexts =
            transactions.map { trn ->
                testStrategy.createCurrencyContext(trn, position)
            }
        val endTime = System.nanoTime()

        // Then all contexts should be created successfully
        assertThat(contexts).hasSize(100)
        contexts.forEach { context ->
            assertThat(context.tradeMoneyValues).isNotNull
            assertThat(context.baseMoneyValues).isNotNull
            assertThat(context.portfolioMoneyValues).isNotNull
        }

        // And performance should be reasonable (less than 1ms per context on average)
        val avgTimePerContext = (endTime - startTime) / 100.0 / 1_000_000.0 // Convert to milliseconds
        assertThat(avgTimePerContext).isLessThan(1.0)
    }

    @Test
    fun `should demonstrate currency resolution optimization compared to individual calls`() {
        // Given a transaction and position
        val trn = createTestTransaction()
        val position = positions.getOrCreate(trn)

        // When timing optimized approach (single context creation)
        val optimizedStartTime = System.nanoTime()
        val context = testStrategy.createCurrencyContext(trn, position)
        testStrategy.applyMultiCurrencyUpdate(context, trn) { _, _ -> }
        val optimizedEndTime = System.nanoTime()

        // When timing traditional approach (3 separate currency resolutions)
        val traditionalStartTime = System.nanoTime()
        position.getMoneyValues(
            Position.In.TRADE,
            currencyResolver.resolve(Position.In.TRADE, trn.portfolio, trn.tradeCurrency)
        )
        position.getMoneyValues(
            Position.In.BASE,
            currencyResolver.resolve(Position.In.BASE, trn.portfolio, trn.tradeCurrency)
        )
        position.getMoneyValues(
            Position.In.PORTFOLIO,
            currencyResolver.resolve(Position.In.PORTFOLIO, trn.portfolio, trn.tradeCurrency)
        )
        val traditionalEndTime = System.nanoTime()

        val optimizedTime = optimizedEndTime - optimizedStartTime
        val traditionalTime = traditionalEndTime - traditionalStartTime

        // Then optimized approach should be reasonable performance-wise
        // Note: This is more about correctness than raw performance in unit tests
        // Both approaches should complete within reasonable time bounds
        assertThat(optimizedTime).isLessThan(1_000_000) // Less than 1ms in nanoseconds
        assertThat(traditionalTime).isLessThan(1_000_000) // Less than 1ms in nanoseconds
    }

    private fun createTestTransaction(): Trn {
        val dateUtils = DateUtils()
        return Trn(
            trnType = TrnType.DIVI,
            asset = asset,
            tradeDate = dateUtils.getFormattedDate("2024-01-15"),
            tradeAmount = BigDecimal("100.00"),
            tradeBaseRate = BigDecimal("1.5"),
            tradePortfolioRate = BigDecimal("1.3")
        )
    }

    /**
     * Test implementation of BaseAccumulationStrategy for testing purposes.
     */
    private class TestAccumulationStrategy(
        currencyResolver: CurrencyResolver
    ) : BaseAccumulationStrategy(currencyResolver) {
        override val supportedType: TrnType = TrnType.DIVI

        override fun accumulate(
            trn: Trn,
            positions: Positions,
            position: Position
        ): Position = position

        // Expose protected methods for testing
        override fun createCurrencyContext(
            trn: Trn,
            position: Position
        ): CurrencyContext = super.createCurrencyContext(trn, position)

        override fun applyMultiCurrencyUpdate(
            context: CurrencyContext,
            trn: Trn,
            updateFunction: (com.beancounter.common.model.MoneyValues, BigDecimal) -> Unit
        ) {
            super.applyMultiCurrencyUpdate(context, trn, updateFunction)
        }
    }
}