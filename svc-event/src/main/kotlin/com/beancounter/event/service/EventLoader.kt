package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.PriceRequest.Companion.dateUtils
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.event.common.DateSplitter
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * loads and stores events from the Market Data service.
 */
@Service
class EventLoader(
    private val portfolioService: PortfolioServiceClient,
    private val backfillService: BackfillService,
    private val positionService: PositionService,
    private val priceService: PriceService,
    private val eventService: EventService,
    private val loginService: LoginService,
) {
    private val dateSplitter = DateSplitter(dateUtils)

    @Async("applicationTaskExecutor")
    fun loadEvents(date: String) {
        loginService.login() // m2m login
        val portfolios = portfolioService.portfolios
        for (portfolio in portfolios.data) {
            loadEvents(portfolio.id, date)
        }
    }

    fun loadEvents(portfolioId: String, date: String) {
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        val dates = dateSplitter.dateRange(date, "today")
        for (processDate in dates) {
            val events = loadEvents(portfolio, processDate)
            log.info("Loaded $events new events")
        }
    }

    fun loadEvents(portfolio: Portfolio, date: LocalDate): Int {
        val positionResponse = positionService.getPositions(portfolio, date.toString())
        log.debug("Analyzing portfolio: ${portfolio.code}, positions: ${positionResponse.data.positions.size}, asAt: $date")
        var totalEvents = 0
        for (position in positionResponse.data.positions.values) {
            val events = load(position, date)
            if (events != 0) {
                backfillService.backFillEvents(portfolio.id, date.toString())
            }
            totalEvents += events
        }
        log.debug("Completed... code: ${portfolio.code}, id: ${portfolio.id}. Wrote $totalEvents missing events")
        return totalEvents
    }

    private fun load(position: Position, date: LocalDate): Int {
        var eventCount = 0
        if (positionService.includePosition(position)) {
//            log.trace("Load events for ${position.asset.name}, ${position.asset.id}")
            val events = priceService.getEvents(position.asset.id)
            for (priceResponse in events.data) {
                if (date.compareTo(priceResponse.priceDate!!) == 0) {
                    eventCount++
                    eventService.save(
                        CorporateEvent(
                            trnType = if (priceResponse.isSplit()) TrnType.SPLIT else TrnType.DIVI,
                            recordDate = priceResponse.priceDate!!,
                            assetId = position.asset.id,
                            rate = priceResponse.dividend,
                            split = priceResponse.split,
                        ),
                    )
                }
            }
            if (eventCount != 0) {
                log.debug("Loaded $eventCount events for asset ${position.asset.id}, ${position.asset.name}")
            }
        }
        return eventCount
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
