package com.beancounter.event.backfill

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.EventDistribution
import com.beancounter.event.distribution.PositionService
import com.beancounter.event.metrics.EventMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Find events that exist locally and resubmit the transactions.
 */
@Service
class BackFillService(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val eventStore: CorporateEventStore,
    private val eventDistribution: EventDistribution,
    private val dateSplitter: DateSplitter,
    private val eventMetrics: EventMetrics
) {
    private val log = LoggerFactory.getLogger(BackFillService::class.java)

    fun backFillEvents(
        portfolioId: String,
        date: String = DateUtils.TODAY,
        toDate: String = date
    ) {
        val dates =
            dateSplitter.dateRange(
                date,
                toDate
            )
        val portfolio =
            portfolioService.getPortfolioById(portfolioId)
        log.debug("BackFill code: ${portfolio.code}, id: ${portfolio.id}")
        var eventCount = 0
        for (asAtDate in dates) {
            val positionResponse =
                positionService.getPositions(
                    portfolio,
                    asAtDate.toString()
                )
            val assets: MutableCollection<String> = mutableListOf()
            for (position in positionResponse.data.positions.values) {
                if (positionService.includePosition(position)) {
                    assets.add(position.asset.id)
                }
            }
            val events =
                eventStore.find(
                    assets,
                    asAtDate
                )
            for (event in events) {
                log.trace("Publish events: ${events.size}, asAt: $asAtDate")
                eventDistribution.processEvent(event)
                eventCount += events.size
            }
        }
        // Record backfill operation metrics
        eventMetrics.recordBackfillOperation(eventCount)
        log.debug("BackFilled portfolio: ${portfolio.code}, events: $eventCount completed")
    }
}