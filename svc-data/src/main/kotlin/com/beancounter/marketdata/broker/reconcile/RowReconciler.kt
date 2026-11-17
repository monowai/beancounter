package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.model.Asset
import com.beancounter.common.model.TrnType
import java.math.BigDecimal
import java.math.RoundingMode

/** Decides what one shown holding needs, given the asset it names and the quantity held. */
internal object RowReconciler {
    /**
     * Half a cent: the most a money figure printed to the cent can be off by. Every
     * tolerance is built from printed rounding, never a percentage, so a misread
     * digit shows however large the holding.
     */
    private val HALF_CENT = BigDecimal("0.005")

    /** A gain or loss may be worked from unrounded figures, so it may be a cent from the printed ones. */
    private val CENT = BigDecimal("0.01")

    /** Money is printed to at least the cent; a client may have dropped trailing zeros. */
    private const val MONEY_SCALE = 2

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
        // The price is rounded for display; the market value may be worked from the unrounded one.
        val tolerance = quantity.abs().multiply(halfUnit(price)).add(HALF_CENT)
        return quantity.multiply(price).subtract(marketValue).abs() > tolerance
    }

    private fun gainLossMismatch(row: ShownHolding): Boolean {
        val marketValue = row.marketValue
        val cost = totalCost(row)
        val gainLoss = row.gainLoss
        if (marketValue == null || cost == null || gainLoss == null) return false
        return marketValue.subtract(cost).subtract(gainLoss).abs() > CENT.add(perShareRounding(row))
    }

    /** What an average cost rounded for display adds up to across the holding; zero when a cost basis is shown. */
    private fun perShareRounding(row: ShownHolding): BigDecimal {
        val costPerShare = row.costPerShare
        val quantity = row.quantity
        if (row.costBasis != null || costPerShare == null || quantity == null) return BigDecimal.ZERO
        return quantity.abs().multiply(halfUnit(costPerShare))
    }

    /** Half the last printed digit of a money figure: 0.005 for 523.88, 0.0005 for 4.505. */
    private fun halfUnit(printed: BigDecimal): BigDecimal =
        HALF_CENT.movePointLeft(maxOf(printed.scale(), MONEY_SCALE) - MONEY_SCALE)
}