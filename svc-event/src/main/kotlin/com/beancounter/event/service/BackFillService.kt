package com.beancounter.event.service

import com.beancounter.event.common.DateSplitter
import com.beancounter.event.config.BackFillServiceConfig
import com.beancounter.event.metrics.EventMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Find events that exist locally and resubmit the transactions.
 */
@Service
class BackFillService(
    config: BackFillServiceConfig,
    private val eventMetrics: EventMetrics
) {
    private val portfolioService = config.sharedConfig.portfolioService
    private val positionService = config.positionService
    private val eventService = config.eventService
    private val tokenService = config.tokenService
    private val dateUtils = config.sharedConfig.dateUtils

    private val dateSplitter = DateSplitter(dateUtils)
    private val log = LoggerFactory.getLogger(BackFillService::class.java)

    fun backFillEvents(
        portfolioId: String,
        date: String = "today",
        toDate: String = date
    ) {
        val dates =
            dateSplitter.dateRange(
                date,
                toDate
            )
        val portfolio =
            portfolioService.getPortfolioById(
                portfolioId,
                tokenService.bearerToken
            )
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
                eventService.find(
                    assets,
                    asAtDate
                )
            for (event in events) {
                log.trace("Publish events: ${events.size}, asAt: $asAtDate")
                eventService.processEvent(event)
                eventCount += events.size
            }
        }
        // Record backfill operation metrics
        eventMetrics.recordBackfillOperation(eventCount)
        log.debug("BackFilled portfolio: ${portfolio.code}, events: $eventCount completed")
    }
}