package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.CashUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to Add a balance transaction into a position.
 * These are psuedo cash transactions that do not support cashAssetId.
 */
@Service
class BalanceBehaviour(
    val currencyResolver: CurrencyResolver,
    private val cashUtils: CashUtils = CashUtils()
) : AccumulationStrategy {
    private val cashCost = CashCost()
    override val supportedType: TrnType
        get() = TrnType.BALANCE

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.quantityValues.purchased = trn.tradeAmount
        // Composite policies (CPF / ILP / generic pension) carry a per-bucket
        // map on the BALANCE trn. Snapshot semantics: overwrite (not add) so
        // re-running with a fresh balance replaces the previous snapshot,
        // mirroring `quantityValues.purchased` above.
        if (!trn.subAccounts.isNullOrEmpty()) {
            position.subAccounts.clear()
            position.subAccounts.putAll(trn.subAccounts!!)
        }
        val isCash = cashUtils.isCash(position.asset)
        applyMoneyValues(
            Position.In.BASE,
            trn,
            position,
            trn.tradeBaseRate,
            isCash
        )
        applyMoneyValues(
            Position.In.PORTFOLIO,
            trn,
            position,
            trn.tradePortfolioRate,
            isCash
        )
        applyMoneyValues(
            Position.In.TRADE,
            trn,
            position,
            BigDecimal.ZERO,
            isCash
        )
        return position
    }

    private fun applyMoneyValues(
        pool: Position.In,
        trn: Trn,
        position: Position,
        rate: BigDecimal,
        isCash: Boolean
    ) {
        val moneyValues =
            currencyResolver.getMoneyValues(
                pool,
                trn.tradeCurrency,
                trn.portfolio,
                position
            )
        // Capture the prior cost basis before CashCost.value resets it; a
        // non-zero prior means a previous BALANCE already set the principal,
        // so this snapshot grows cost by its contribution rather than
        // re-pinning the whole balance as gain.
        val priorCostBasis = moneyValues.costBasis
        cashCost.value(
            moneyValues,
            position,
            trn.tradeAmount,
            rate
        )
        if (!isCash) {
            setSnapshotCost(
                moneyValues,
                trn.tradeAmount,
                trn.contribution,
                rate,
                priorCostBasis
            )
        }
    }

    /**
     * Non-cash BALANCE cost basis.
     *
     * A BALANCE is a snapshot of a contribution-driven account (CPF, pension):
     * its increase between snapshots is mostly FRESH PRINCIPAL (contributions),
     * not market gain. Pinning cost at the first snapshot and treating every
     * later rise as gain therefore reports absurd growth (e.g. +3340%).
     *
     * Instead:
     * - First snapshot: cost = market value, so the starting balance is the
     *   principal and gain = 0 (no phantom 100% gain on a freshly linked
     *   position).
     * - Later snapshots: grow cost by the [contribution] recognised since the
     *   prior snapshot (supplied by svc-data from the asset's contribution
     *   config), so only interest surfaces as unrealised gain. Capped at the
     *   current market value so an over-stated contribution estimate can't
     *   show a phantom loss. When no contribution is supplied, cost tracks the
     *   balance (gain = 0) — never a phantom gain.
     */
    private fun setSnapshotCost(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        contribution: BigDecimal?,
        rate: BigDecimal,
        priorCostBasis: BigDecimal
    ) {
        val marketValue = (MathUtils.multiply(tradeAmount, rate) ?: return).abs()
        val cost =
            if (priorCostBasis.signum() == 0 || contribution == null) {
                marketValue
            } else {
                val contributed = (MathUtils.multiply(contribution, rate) ?: BigDecimal.ZERO).abs()
                (priorCostBasis + contributed).min(marketValue)
            }
        moneyValues.costBasis = cost
        moneyValues.costValue = cost
        moneyValues.averageCost = BigDecimal.ONE
    }
}