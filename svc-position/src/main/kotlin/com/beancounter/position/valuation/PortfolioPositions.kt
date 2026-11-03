package com.beancounter.position.valuation

import com.beancounter.auth.TokenService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.holdings.PositionService
import com.beancounter.position.marketvalue.MarketValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * A portfolio's positions as at a date: its trns from bc-data accumulated, with
 * the earmarked cash of its proposed legs stamped on before any valuation.
 */
@Component
class PortfolioPositions(
    private val trnService: TrnService,
    private val positionService: PositionService,
    private val earmarkService: EarmarkService,
    private val tokenService: TokenService
) {
    /** Positions from an ad hoc trn query, dated at the query's trade date. */
    fun of(trnQuery: TrustedTrnQuery): Positions {
        val valuationDate = trnQuery.tradeDate.toString()
        val positions =
            accumulate(
                trnQuery.portfolio,
                trnService.query(trnQuery).data.toTrns(),
                proposedLegs(trnQuery.portfolio, valuationDate)
            )
        return dated(positions, valuationDate)
    }

    fun of(
        portfolio: Portfolio,
        valuationDate: String
    ): Positions {
        val reads = read(listOf(portfolio), valuationDate).single()
        return dated(accumulate(portfolio, reads.trns, reads.proposedLegs), valuationDate)
    }

    /**
     * Each portfolio's positions, accumulated on its own. A portfolio with no trns
     * is left out.
     */
    fun each(
        portfolios: Collection<Portfolio>,
        valuationDate: String
    ): List<Pair<Portfolio, Positions>> =
        read(portfolios, valuationDate)
            .filter { it.trns.isNotEmpty() }
            .map { it.portfolio to accumulate(it.portfolio, it.trns, it.proposedLegs) }

    /**
     * Reads each portfolio's trns and proposed cash legs from bc-data, every
     * call at once. Neither read depends on the other, and the clients block,
     * so they only overlap on the IO pool, each bound to the caller.
     */
    private fun read(
        portfolios: Collection<Portfolio>,
        valuationDate: String
    ): List<PortfolioReads> {
        val caller = SecurityContextHolder.getContext()
        return runBlockingTraced(Dispatchers.IO) {
            portfolios
                .map { portfolio ->
                    Triple(
                        portfolio,
                        async { caller.runAs { trnService.query(portfolio, valuationDate) } },
                        async { caller.runAs { proposedLegs(portfolio, valuationDate) } }
                    )
                }.map { (portfolio, trns, legs) -> PortfolioReads(portfolio, trns.await().data.toTrns(), legs.await()) }
        }
    }

    /**
     * [trns] accumulated into positions, with [legs]' earmarked cash-ccy quantity stamped
     * BEFORE market value runs so MarketValue can mint MoneyValues.earmarked per bucket.
     */
    private fun accumulate(
        portfolio: Portfolio,
        trns: List<Trn>,
        legs: List<Trn>
    ): Positions {
        val positions = positionService.build(portfolio, PositionRequest(portfolio.id, trns)).data
        earmarkService.stamp(positions, legs)
        return positions
    }

    private fun dated(
        positions: Positions,
        valuationDate: String
    ): Positions {
        if (!valuationDate.equals(DateUtils.TODAY, ignoreCase = true)) {
            positions.asAt = valuationDate
        }
        return positions
    }

    /**
     * Earmarked cash is an overlay on the cash position, read from bc-data's
     * proposed-cash route with the caller's own token. A home-screen widget's
     * token carries only `beancounter:widget`, which has no right to that route
     * (it is not in bc-data's `auth.widget-routes`), and the widget shows
     * percentages only and never displays the overlay. So a widget-scoped
     * valuation skips the read outright rather than taking a 403 per portfolio;
     * enough of those once opened the shared bc-data circuit breaker.
     */
    private fun proposedLegs(
        portfolio: Portfolio,
        valuationDate: String
    ): List<Trn> =
        if (tokenService.isWidgetToken) {
            emptyList()
        } else {
            earmarkService.proposedLegs(portfolio, valuationDate)
        }
}

/** One portfolio's bc-data reads for a valuation. */
private class PortfolioReads(
    val portfolio: Portfolio,
    val trns: List<Trn>,
    val proposedLegs: List<Trn>
)