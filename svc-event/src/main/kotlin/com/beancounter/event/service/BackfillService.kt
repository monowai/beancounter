package com.beancounter.event.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.common.DateSplitter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Re-Process all events recorded in this service
 */
@Service
class BackfillService(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val eventService: EventService
) {
    private val dateUtils = DateUtils()
    private val dateSplitter = DateSplitter(dateUtils)
    private val log = LoggerFactory.getLogger(BackfillService::class.java)

    @Async("applicationTaskExecutor")
    fun backFillEvents(portfolioId: String, date: String = "today", toDate: String = date) {
        val asAt: String = if (date.equals(DateUtils.today, ignoreCase = true)) {
            dateUtils.today()
        } else {
            dateUtils.getDate(date).toString()
        }
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        val dates = dateSplitter.split(from = asAt, until = toDate, days = 1)
        log.debug("Started backfill code: ${portfolio.code}, id: ${portfolio.id}, days: ${dates.size}")
        var eventCount = 0
        var dayCount = 0
        for (asAtDate in dates) {
            dayCount++
            val positionResponse = positionService.getPositions(portfolio, asAtDate.toString())
            val assets: MutableCollection<String> = mutableListOf()
            for (position in positionResponse.data.positions.values) {
                if (positionService.includePosition(position)) assets.add(position.asset.id)
            }
            val events = eventService.find(assets, asAtDate)
            for (event in events) {
                log.trace("Loading events: ${events.size}, asAt: $asAtDate")
                eventService.processEvent(event)
                eventCount += events.size
            }
        }
        log.info("Processed $eventCount events over $dayCount days")
    }
}
