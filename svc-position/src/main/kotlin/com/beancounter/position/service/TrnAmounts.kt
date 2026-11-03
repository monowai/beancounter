package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import java.math.BigDecimal
import java.math.RoundingMode

// A trn's amounts in its portfolio's currency, converted through the rates stored on the trn.

private const val CASH_LEG_SCALE = 10
private const val MONEY_SCALE = 2

/** The unsigned trade amount, trade -> portfolio via tradePortfolioRate; unconverted when no rate is stored. */
internal fun Trn.tradeAmountIn(portfolio: Portfolio): BigDecimal {
    val amount = tradeAmount.abs()
    if (tradeCurrency.code == portfolio.currency.code) return amount
    val rate = tradePortfolioRate
    return if (rate.signum() != 0) {
        amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    } else {
        amount
    }
}

/**
 * The trade's signed cash leg (cash currency) in portfolio currency, through the
 * trade's stored rates: cash -> trade via tradeCashRate, then trade -> portfolio
 * via tradePortfolioRate.
 */
internal fun Trn.cashLegIn(portfolio: Portfolio): BigDecimal {
    val cashCode = (cashCurrency ?: tradeCurrency).code
    if (cashCode == portfolio.currency.code) return cashAmount
    val inTrade =
        if (cashCode == tradeCurrency.code || tradeCashRate.signum() == 0) {
            cashAmount
        } else {
            cashAmount.divide(tradeCashRate, CASH_LEG_SCALE, RoundingMode.HALF_UP)
        }
    val inPortfolio =
        if (tradeCurrency.code == portfolio.currency.code) inTrade else inTrade.multiply(tradePortfolioRate)
    return inPortfolio.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
}

/**
 * An external cash flow's amount in portfolio currency, signed money in positive and
 * money out negative, converted with the trn's stored tradePortfolioRate.
 */
internal fun Trn.externalFlowIn(portfolio: Portfolio): BigDecimal {
    // A DEPOSIT, WITHDRAWAL or DEDUCTION moves the cash balance by its quantity
    // (see DepositBehaviour), so the flow is that quantity too. Taking it from
    // cashAmount let the two drift apart, and the difference read as return.
    val rawAmount =
        when {
            TrnType.isCash(trnType) && quantity.signum() != 0 -> quantity
            cashAmount.signum() != 0 -> cashAmount
            else -> tradeAmount
        }
    // Normalize sign: cashAmount sign is inconsistent across import sources, so enforce here.
    val amount = trnType.signedFlow(rawAmount)
    if (tradeCurrency.code == portfolio.currency.code) return amount
    val rate = tradePortfolioRate
    return if (rate.signum() != 0) {
        amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    } else {
        amount
    }
}

/**
 * The sign TWR gives an external flow:
 *   DEPOSIT/INCOME  -> positive (money in)
 *   WITHDRAWAL/DEDUCTION/EXPENSE -> negative (money out)
 */
private fun TrnType.signedFlow(amount: BigDecimal): BigDecimal =
    when (this) {
        TrnType.DEPOSIT, TrnType.INCOME -> amount.abs()
        TrnType.WITHDRAWAL, TrnType.DEDUCTION, TrnType.EXPENSE -> amount.abs().negate()
        else -> amount
    }