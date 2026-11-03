package com.beancounter.position.service

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The line time-weighted return draws around a portfolio: which assets it measures,
 * and which trns move money across the line as external flows rather than earn a
 * return inside it.
 */
@Component
class TwrBoundary {
    private val cashUtils = CashUtils()

    /**
     * PRIVATE-market assets stay out of TWR unless [Asset.includeInPerformance]
     * is set: their valuations move on appraisal or snapshot cycles, not market prices.
     * Cash-like PRIVATE assets (bank accounts) are cash and always stay in.
     */
    fun isExcluded(asset: Asset): Boolean =
        asset.market.code == PRIVATE_MARKET && !cashUtils.isCash(asset) && !asset.includeInPerformance

    /** A trn whose trade date must be a valuation date because it carries a flow. */
    fun isFlowEvent(trn: Trn): Boolean =
        isExternalCashFlow(trn.trnType) ||
            (trn.trnType == TrnType.BALANCE && !isExcluded(trn.asset)) ||
            isExcludedCashLeg(trn) ||
            isSettledOutside(trn) ||
            isInKindTransfer(trn)

    fun isExcludedCashLeg(trn: Trn): Boolean =
        isExcluded(trn.asset) && trn.cashAsset != null && TrnType.isCashImpacted(trn.trnType)

    /**
     * A cash-impacting trade in an included asset that names no cash asset: the
     * accumulator moves no cash for it, so the money passed outside the portfolio.
     */
    fun isSettledOutside(trn: Trn): Boolean =
        trn.cashAsset == null &&
            TrnType.isCashImpacted(trn.trnType) &&
            !isExternalCashFlow(trn.trnType) &&
            !cashUtils.isCash(trn.asset) &&
            !isExcluded(trn.asset)

    /** An ADD or REDUCE of an included asset: units moved in or out without cash. */
    fun isInKindTransfer(trn: Trn): Boolean =
        (trn.trnType == TrnType.ADD || trn.trnType == TrnType.REDUCE) && !isExcluded(trn.asset)

    /** Money in for a buy, money out for sale proceeds or a dividend, in portfolio currency. */
    fun settledOutsideFlow(
        trn: Trn,
        portfolio: Portfolio
    ): BigDecimal {
        val amount = trn.tradeAmountIn(portfolio)
        return if (TrnType.isCashCredited(trn.trnType)) amount.negate() else amount
    }

    /**
     * The basis a BALANCE on an included asset restates, in portfolio currency: the
     * balance itself for cash, the cost basis otherwise. Null for any other trn.
     */
    fun balanceBasis(
        trn: Trn,
        positions: Positions,
        portfolio: Portfolio
    ): BigDecimal? {
        if (trn.trnType != TrnType.BALANCE || isExcluded(trn.asset)) return null
        val position = positions.getOrCreate(trn)
        if (!cashUtils.isCash(trn.asset)) {
            return position.moneyValues[Position.In.PORTFOLIO]?.costBasis ?: BigDecimal.ZERO
        }
        val balance = position.quantityValues.getTotal()
        return if (trn.tradeCurrency.code == portfolio.currency.code) {
            balance
        } else {
            balance.multiply(trn.tradePortfolioRate).setScale(2, RoundingMode.HALF_UP)
        }
    }

    companion object {
        // Matches PrivateMarketDataProvider.ID in svc-data: single latest-price
        // row, no daily history.
        const val PRIVATE_MARKET = "PRIVATE"

        /**
         * External cash flows are money entering or leaving the portfolio from outside.
         * These break TWR sub-periods. BUY/SELL are internal (cash <-> equity) and don't.
         */
        fun isExternalCashFlow(trnType: TrnType): Boolean =
            trnType == TrnType.DEPOSIT ||
                trnType == TrnType.WITHDRAWAL ||
                trnType == TrnType.INCOME ||
                trnType == TrnType.DEDUCTION ||
                trnType == TrnType.EXPENSE
    }
}