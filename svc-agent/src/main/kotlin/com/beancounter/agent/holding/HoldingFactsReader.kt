package com.beancounter.agent.holding

import com.beancounter.agent.HoldingAnalysisSubject
import com.beancounter.agent.client.PositionClient
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Where a Holding Analysis reads the caller's holding. */
fun interface HoldingFactsSource {
    /** The caller's open holding of [subject], or null when they hold none they can see. */
    fun find(subject: HoldingAnalysisSubject): HoldingFacts?

    companion object {
        /** Holds nothing: every Holding Analysis is not-held. */
        val NONE = HoldingFactsSource { null }
    }
}

/**
 * Reads the holding from svc-position with the caller's own token, as
 * [com.beancounter.agent.tools.HoldingTools] does, so svc-position decides
 * what the caller may see. A portfolio it will not show them (missing, or
 * someone else's) and an asset it does not hold, or holds no more, are all
 * the same answer: not held.
 */
@Service
class HoldingFactsReader(
    private val positionClient: PositionClient
) : HoldingFactsSource {
    private val log = LoggerFactory.getLogger(HoldingFactsReader::class.java)

    override fun find(subject: HoldingAnalysisSubject): HoldingFacts? {
        val positions = positionsOf(subject.portfolioCode) ?: return null
        val position =
            positions.positions.values.singleOrNull {
                it.asset.code.equals(subject.ticker, ignoreCase = true) &&
                    (
                        subject.market.isEmpty() ||
                            it.asset.market.code
                                .equals(subject.market, ignoreCase = true)
                    ) &&
                    it.quantityValues.hasPosition()
            } ?: return null
        return factsOf(positions, position)
    }

    /** The portfolio's valued positions, or null when svc-position will not show it to the caller. */
    private fun positionsOf(portfolioCode: String): Positions? =
        try {
            positionClient.getPositionsByCode(portfolioCode, DateUtils.TODAY, includeValues = true).data
        } catch (e: NotFoundException) {
            log.debug("Portfolio {} not found for a holding analysis: {}", portfolioCode, e.message)
            null
        } catch (e: ForbiddenException) {
            log.debug("Portfolio {} refused for a holding analysis: {}", portfolioCode, e.message)
            null
        }

    private fun factsOf(
        positions: Positions,
        position: Position
    ): HoldingFacts? {
        val trade = position.moneyValues[Position.In.TRADE] ?: return null
        val inPortfolio = position.moneyValues[Position.In.PORTFOLIO] ?: return null
        val portfolio = positions.portfolio
        val close = trade.priceData.close.takeIf { it.signum() > 0 }
        return HoldingFacts(
            portfolioCode = portfolio.code,
            portfolioName = portfolio.name,
            portfolioCurrency = inPortfolio.currency.code,
            assetCode = position.asset.code,
            assetName = position.asset.name ?: position.asset.code,
            market = position.asset.market.code,
            tradeCurrency = trade.currency.code,
            weight = inPortfolio.weight,
            averageCost = trade.averageCost,
            currentPrice = close,
            priceDate = close?.let { trade.priceData.priceDate },
            unrealisedGainRatio = HoldingFacts.ratio(inPortfolio.unrealisedGain, inPortfolio.costValue),
            opened = position.dateValues.opened
        )
    }
}