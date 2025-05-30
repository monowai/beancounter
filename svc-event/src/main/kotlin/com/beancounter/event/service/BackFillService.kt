package com.beancounter.event.service

import com.beancounter.auth.TokenService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.common.DateSplitter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Find events that exist locally and resubmit the transactions.
 */
@Service
class BackFillService(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val eventService: EventService,
    private val tokenService: TokenService
) {
    private val dateUtils = DateUtils()
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
        log.trace("BackFilled portfolio: ${portfolio.code}, events: $eventCount completed")
    }
}