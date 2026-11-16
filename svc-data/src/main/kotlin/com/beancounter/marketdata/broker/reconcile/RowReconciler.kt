package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import java.math.BigDecimal
import java.math.RoundingMode

/** Decides what one shown holding needs, given the asset it names and the quantity held. */
internal object RowReconciler {
    /** Quantity x price may differ from the shown market value by this fraction (0 to 1) of it. */
    val MARKET_VALUE_TOLERANCE_RATE = BigDecimal("0.01")

    /** Market value less total cost may differ from the shown gain or loss by this amount. */
    val GAIN_LOSS_TOLERANCE = BigDecimal("1.00")

    private const val COST_SCALE = 2

    private data class Proposal(
        val status: RowStatus,
        val trnType: TrnType? = null,
        val quantity: BigDecimal? = null,
        val cost: BigDecimal? = null
    )

    /**
     * @param market the market the row trades on, null when neither the row nor the firm names one
     * @param asset the asset [row] names on [market], null when unknown
     * @param held the quantity of [asset] held at the caller's broker
     */
    fun reconcile(
        row: ShownHolding,
        market: String?,
        asset: Asset?,
        held: BigDecimal
    ): PreviewRow {
        val proposal = proposal(row, market, asset, held)
        return PreviewRow(
            code = row.code,
            market = market,
            assetId = asset?.id,
            assetName = asset?.name,
            shown = row.quantity,
            held = held,
            trnType = proposal.trnType,
            quantity = proposal.quantity,
            cost = proposal.cost,
            status = proposal.status,
            warnings = warnings(row)
        )
    }

    private fun proposal(
        row: ShownHolding,
        market: String?,
        asset: Asset?,
        held: BigDecimal
    ): Proposal {
        val shown = row.quantity
        return when {
            // A negative holding is a short position or a misread sign: neither is recorded.
            shown == null || shown.signum() < 0 -> Proposal(RowStatus.NEEDS_QUANTITY)
            market == null -> Proposal(RowStatus.NEEDS_MARKET)
            asset == null -> Proposal(RowStatus.UNRESOLVED)
            else -> change(row, shown, held)
        }
    }

    private fun change(
        row: ShownHolding,
        shown: BigDecimal,
        held: BigDecimal
    ): Proposal {
        val difference = shown.subtract(held)
        return when (difference.signum()) {
            0 -> {
                Proposal(RowStatus.UNCHANGED)
            }
            1 -> {
                val cost = costOf(difference, row, shown)
                Proposal(if (cost == null) RowStatus.NEEDS_COST else RowStatus.READY, TrnType.ADD, difference, cost)
            }
            else -> {
                Proposal(RowStatus.READY, TrnType.REDUCE, difference.negate())
            }
        }
    }

    /** The shown holding's total cost: its cost basis, else cost per share x quantity. */
    private fun totalCost(row: ShownHolding): BigDecimal? =
        row.costBasis ?: row.quantity?.let { row.costPerShare?.multiply(it) }

    /** The share of the shown holding's total cost that [quantity] of it carries. */
    private fun costOf(
        quantity: BigDecimal,
        row: ShownHolding,
        shown: BigDecimal
    ): BigDecimal? =
        totalCost(row)
            ?.takeIf { shown.signum() != 0 }
            ?.multiply(quantity)
            ?.divide(shown, COST_SCALE, RoundingMode.HALF_UP)

    private fun warnings(row: ShownHolding): List<RowWarning> =
        listOfNotNull(
            RowWarning.MARKET_VALUE_MISMATCH.takeIf { marketValueMismatch(row) },
            RowWarning.GAIN_LOSS_MISMATCH.takeIf { gainLossMismatch(row) }
        )

    private fun marketValueMismatch(row: ShownHolding): Boolean {
        val quantity = row.quantity
        val price = row.price
        val marketValue = row.marketValue
        if (quantity == null || price == null || marketValue == null) return false
        val tolerance = marketValue.abs().multiply(MARKET_VALUE_TOLERANCE_RATE)
        return quantity.multiply(price).subtract(marketValue).abs() > tolerance
    }

    private fun gainLossMismatch(row: ShownHolding): Boolean {
        val marketValue = row.marketValue
        val cost = totalCost(row)
        val gainLoss = row.gainLoss
        if (marketValue == null || cost == null || gainLoss == null) return false
        return marketValue.subtract(cost).subtract(gainLoss).abs() > GAIN_LOSS_TOLERANCE
    }
}