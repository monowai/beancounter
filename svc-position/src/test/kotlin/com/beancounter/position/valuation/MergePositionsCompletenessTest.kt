package com.beancounter.position.valuation

import com.beancounter.common.model.Position
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

/**
 * Guards ValuationService.mergePositions against silently dropping Position
 * state in the aggregated (multi-portfolio) view.
 *
 * mergePositions rebuilds the aggregate Position field by field, so every
 * property added to Position must be consciously classified here: either it
 * is merged, or it is explicitly excluded with a reason. A new field that is
 * neither fails this test — which is exactly the bug that shipped when
 * `held` (per-broker quantities) was added to Position but never merged,
 * leaving the aggregated sell dialog without broker holdings.
 */
class MergePositionsCompletenessTest {
    /** Properties mergePositions carries into the aggregate. */
    private val merged =
        setOf(
            "quantityValues", // mergeQuantities
            "moneyValues", // mergeMoneyValues per bucket, FX-converted
            "periodicCashFlows", // addAll
            "held", // per-broker quantities, summed by broker name
            "subAccounts", // per-sub-account balances, summed by code
            "dateValues", // mergeDates (earliest firstTransaction/opened)
            "earmarkedQuantity" // signed net PROPOSED cash-leg qty, summed in mergeQuantities
        )

    /** Properties intentionally not merged. */
    private val excluded =
        setOf(
            "asset", // aggregate identity — positions are keyed by it
            "portfolioBreakdown" // derived AFTER merge by applyPortfolioBreakdown
        )

    @Test
    fun `every Position property is either merged or explicitly excluded`() {
        val properties = Position::class.memberProperties.map { it.name }.toSet()

        assertThat(merged.intersect(excluded)).isEmpty()
        assertThat(merged + excluded)
            .withFailMessage(
                "Position properties changed. For each new property, either merge it in " +
                    "ValuationService.mergePositions and add it to `merged`, or justify it in " +
                    "`excluded`. Unclassified: %s — stale entries: %s",
                properties - merged - excluded,
                (merged + excluded) - properties
            ).containsExactlyInAnyOrderElementsOf(properties)
    }
}