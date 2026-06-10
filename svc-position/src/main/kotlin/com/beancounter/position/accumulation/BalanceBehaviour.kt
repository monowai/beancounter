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
        // non-zero prior means a previous BALANCE already pinned cost and
        // subsequent snapshots must leave it alone so gain can emerge.
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
                rate,
                priorCostBasis
            )
        }
    }

    /**
     * Non-cash BALANCE: pin cost basis at the first snapshot ("principal")
     * and leave it alone on subsequent snapshots so MV - cost surfaces real
     * unrealised gain. First snapshot still sets cost = MV (gain = 0) so a
     * freshly linked CPF/policy position doesn't report a phantom 100% gain.
     */
    private fun setSnapshotCost(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal,
        priorCostBasis: BigDecimal
    ) {
        if (priorCostBasis.signum() != 0) {
            moneyValues.costBasis = priorCostBasis
            moneyValues.costValue = priorCostBasis
            return
        }
        val amount = MathUtils.multiply(tradeAmount, rate) ?: return
        moneyValues.costBasis = amount.abs()
        moneyValues.costValue = amount.abs()
        moneyValues.averageCost = BigDecimal.ONE
    }
}