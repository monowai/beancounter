package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.position.utils.CurrencyResolver
import java.math.BigDecimal

/**
 * Base class providing optimized common patterns for accumulation strategies.
 * Reduces currency resolution overhead and consolidates repeated calculations.
 */
abstract class BaseAccumulationStrategy(
    protected val currencyResolver: CurrencyResolver
) : AccumulationStrategy {
    companion object {
        // Cache common BigDecimal values to reduce object creation
        val ONE: BigDecimal = BigDecimal.ONE
        val ZERO: BigDecimal = BigDecimal.ZERO
    }

    /**
     * Cached currency-resolved MoneyValues for a transaction context.
     * Eliminates 3 separate currency resolution calls per strategy.
     */
    data class CurrencyContext(
        val tradeMoneyValues: MoneyValues,
        val baseMoneyValues: MoneyValues,
        val portfolioMoneyValues: MoneyValues
    )

    /**
     * Create optimized currency context with single resolution per currency type.
     * Replaces 3 separate calls to currencyResolver.resolve() across behaviors.
     */
    protected open fun createCurrencyContext(
        trn: Trn,
        position: Position
    ): CurrencyContext {
        // Resolve all currencies once upfront instead of 3 separate calls
        val tradeCurrency = trn.tradeCurrency
        val portfolioCurrency = trn.portfolio.currency
        val baseCurrency = trn.portfolio.base

        return CurrencyContext(
            tradeMoneyValues = position.getMoneyValues(Position.In.TRADE, tradeCurrency),
            baseMoneyValues = position.getMoneyValues(Position.In.BASE, baseCurrency),
            portfolioMoneyValues = position.getMoneyValues(Position.In.PORTFOLIO, portfolioCurrency)
        )
    }

    /**
     * Apply value updates across all currency contexts with optimized rate application.
     * Eliminates repetitive rate multiplication and currency resolution.
     */
    protected open fun applyMultiCurrencyUpdate(
        context: CurrencyContext,
        trn: Trn,
        updateFunction: (MoneyValues, BigDecimal) -> Unit
    ) {
        // Apply updates with pre-calculated rates
        updateFunction(context.tradeMoneyValues, ONE)
        updateFunction(context.baseMoneyValues, trn.tradeBaseRate)
        updateFunction(context.portfolioMoneyValues, trn.tradePortfolioRate)
    }
}