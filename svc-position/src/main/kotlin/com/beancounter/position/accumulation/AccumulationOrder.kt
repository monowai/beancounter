package com.beancounter.position.accumulation

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType

/**
 * The order trns accumulate in. svc-data returns them in trade date order.
 *
 * An imported holding is an ADD whose cost date is before its trade date. It is owed dividends
 * from its cost date, so those dividends are dated before the ADD. In trade date order such a
 * dividend would open an empty position, and the ADD would then re-open it, clearing its cash
 * flows. Each one is moved to just after the ADD instead, so it accumulates onto the holding.
 *
 * Only those dividends move. The ADD, splits and every other trn stay in trade date order, so a
 * split before the ADD's trade date never applies to it. A list without such dividends is
 * returned as it is.
 */
object AccumulationOrder {
    fun of(trns: List<Trn>): List<Trn> {
        val moved = moved(trns)
        if (moved.isEmpty()) return trns
        val following = moved.entries.groupBy({ it.value }, { trns[it.key] })
        return trns.flatMapIndexed { index, trn ->
            if (index in moved) emptyList() else listOf(trn) + following[index].orEmpty()
        }
    }

    // The index of the ADD each moved dividend follows, by the dividend's index.
    private fun moved(trns: List<Trn>): Map<Int, Int> {
        val imports = trns.withIndex().filter { (_, trn) -> isImport(trn) }
        if (imports.isEmpty()) return emptyMap()
        return trns
            .withIndex()
            .filter { (_, trn) -> trn.trnType == TrnType.DIVI }
            .mapNotNull { (index, dividend) ->
                imports
                    .filter { (_, add) -> owes(add, dividend) }
                    .minByOrNull { (_, add) -> add.tradeDate }
                    ?.let { (addIndex, _) -> index to addIndex }
            }.toMap()
    }

    private fun isImport(trn: Trn): Boolean {
        val costDate = trn.costDate ?: return false
        return trn.trnType == TrnType.ADD && costDate < trn.tradeDate
    }

    // A dividend paid from the ADD's cost date up to, but not on, its trade date.
    private fun owes(
        add: Trn,
        dividend: Trn
    ): Boolean {
        val costDate = add.costDate ?: return false
        return add.asset.id == dividend.asset.id &&
            dividend.tradeDate >= costDate &&
            dividend.tradeDate < add.tradeDate
    }
}