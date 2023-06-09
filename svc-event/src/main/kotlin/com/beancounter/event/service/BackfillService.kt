package com.beancounter.event.service

import com.beancounter.auth.TokenService
import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.common.DateSplitter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Find events that exist locally and resubmit the transactions.
 */
@Service
class BackfillService(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val eventService: EventService,
    private val tokenService: TokenService,
    private val loginService: LoginService,
) {
    private val dateUtils = DateUtils()
    private val dateSplitter = DateSplitter(dateUtils)
    private val log = LoggerFactory.getLogger(BackfillService::class.java)

    @Async("applicationTaskExecutor")
    fun backFillEvents(portfolioId: String, date: String = "today", toDate: String = date) {
        val dates = dateSplitter.dateRange(date, toDate)
        loginService.loginM2m() // m2m
        val portfolio =
            portfolioService.getPortfolioById(portfolioId, tokenService.bearerToken)
        log.debug("Started backfill code: ${portfolio.code}, id: ${portfolio.id}, days: ${dates.size}")
        var eventCount = 0
        var dayCount = 0
        for (asAtDate in dates) {
            dayCount++
            val positionResponse = positionService.getPositions(portfolio, asAtDate.toString())
            val assets: MutableCollection<String> = mutableListOf()
            for (position in positionResponse.data.positions.values) {
                if (positionService.includePosition(position)) {
                    assets.add(position.asset.id)
                }
            }
            val events = eventService.find(assets, asAtDate)
            for (event in events) {
                log.trace("Loading events: ${events.size}, asAt: $asAtDate")
                eventService.processEvent(event)
                eventCount += events.size
            }
        }
        log.info("Portfolio ${portfolio.code}, $eventCount events over $dayCount days")
    }
}
