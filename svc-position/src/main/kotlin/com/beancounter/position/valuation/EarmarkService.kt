package com.beancounter.position.valuation

import com.beancounter.client.services.TrnService
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.CashUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Stamps each cash [com.beancounter.common.model.Position] with the signed net
 * quantity of PROPOSED DEPOSIT / WITHDRAWAL legs on that cash asset
 * (DEPOSIT +, WITHDRAWAL −) in the cash asset's own currency.
 *
 * The scalar lives on [com.beancounter.common.model.Position.earmarkedQuantity];
 * MarketValue mints the per-bucket money figure (MoneyValues.earmarked) from it
 * using the SAME per-bucket close it uses for marketValue, so the two stay
 * FX-consistent. Earmarked deliberately does NOT flow through CashAccumulator
 * (that would apply trade-date rates).
 */
@Service
class EarmarkService(
    private val trnService: TrnService,
    private val cashUtils: CashUtils = CashUtils()
) {
    private val log = LoggerFactory.getLogger(EarmarkService::class.java)

    fun stamp(
        portfolio: Portfolio,
        positions: Positions,
        asAt: String
    ) {
        if (!positions.hasPositions()) return
        // Earmark is a supplementary overlay on the cash position; a failure to
        // fetch the PROPOSED legs must never break the core valuation, so degrade
        // to "no earmark" on any client/server error rather than propagating. The
        // bc-data RestClient maps HTTP errors to BC RuntimeExceptions
        // (NotFoundException etc.), so the catch is deliberately broad.
        @Suppress("TooGenericExceptionCaught")
        val legs =
            try {
                trnService.queryProposedCash(portfolio.id, asAt).data.toTrns()
            } catch (e: RuntimeException) {
                log.warn("Earmark skipped for {}: {}", portfolio.code, e.message)
                return
            }
        if (legs.isEmpty()) return

        // Signed net cash-ccy quantity per cash asset, keyed the same way
        // positions.positions is keyed (AssetKeyUtils.toKey) so lookups hit.
        val netByAssetKey =
            legs
                .groupBy { toKey(it.asset) }
                .mapValues { (_, trns) ->
                    trns.fold(BigDecimal.ZERO) { acc, t ->
                        acc +
                            if (TrnType.isCashCredited(t.trnType)) {
                                t.quantity.abs()
                            } else {
                                t.quantity.abs().negate()
                            }
                    }
                }

        positions.positions.forEach { (key, position) ->
            if (cashUtils.isCash(position.asset)) {
                netByAssetKey[key]?.let { position.earmarkedQuantity = it }
            }
        }
    }
}