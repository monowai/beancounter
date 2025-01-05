package com.beancounter.event.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.event.common.DateSplitter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Service
class EventLoader(
    private val portfolioService: PortfolioServiceClient,
    private val backFillService: BackFillService,
    private val positionService: PositionService,
    private val priceService: PriceService,
    private val eventService: EventService,
    private val dateSplitter: DateSplitter
) {
    // private val authContext: OpenIdResponse = loginService.loginM2m()

    fun loadEvents(date: String) {
        val portfolios = portfolioService.portfolios
        log.info("Loading missing events from date: $date for ${portfolios.data.size} portfolios")
        for (portfolio in portfolios.data) {
            loadEvents(portfolio.id, date)
            log.info("Completed event load for ${portfolio.code}")
        }
    }

    fun loadEvents(
        portfolioId: String,
        date: String
    ) {
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        runBlocking {
            val dates = dateSplitter.dateRange(date, "today")
            for (processDate in dates) {
                launch {
                    val events = loadEvents(portfolio, processDate)
                    if (events > 0) {
                        log.trace("Loaded $events events for portfolio: ${portfolio.code}/$portfolioId")
                    }
                }
            }
        }
    }

    private fun loadEvents(
        portfolio: Portfolio,
        date: LocalDate
    ): Int {
        // loginService.setAuthContext(authContext)
        val positionResponse = positionService.getPositions(portfolio, date.toString())
        if (positionResponse.data.positions.values
                .isEmpty()
        ) {
            return 0
        }

        var totalEvents = 0
        val executor: ExecutorService = Executors.newFixedThreadPool(10)
        val tasks: List<Callable<Int>> =
            positionResponse.data.positions.values.map { position ->
                Callable {
                    val events = load(position, date)
                    if (events != 0) {
                        backFillService.backFillEvents(portfolio.id, date.toString())
                        log.trace("Published: $events nominal events")
                    }
                    totalEvents += events
                    events
                }
            }

        executor.invokeAll(tasks)
        executor.shutdown()

        if (totalEvents > 0) {
            log.debug(
                "portfolio: ${portfolio.code}, id: ${portfolio.id}. Dispatched: $totalEvents nominal events, date: $date"
            )
        } else {
            log.trace("No events for portfolio: ${portfolio.code}, id: ${portfolio.id}, date: $date")
        }

        return totalEvents
    }

    private fun load(
        position: Position,
        date: LocalDate
    ): Int {
        var eventCount = 0
        if (positionService.includePosition(position)) {
            // loginService.setAuthContext(authContext)
            val events = priceService.getEvents(position.asset.id)
            for (priceResponse in events.data) {
                if (date.compareTo(priceResponse.priceDate) == 0) {
                    eventCount++
                    eventService.save(
                        CorporateEvent(
                            trnType = if (isSplit(priceResponse)) TrnType.SPLIT else TrnType.DIVI,
                            recordDate = priceResponse.priceDate,
                            assetId = position.asset.id,
                            rate = priceResponse.dividend,
                            split = priceResponse.split
                        )
                    )
                }
            }
            if (eventCount != 0) {
                log.debug("Loaded events: $eventCount, asset: ${position.asset.id}, name: ${position.asset.name}")
            }
        }
        return eventCount
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventLoader::class.java)
    }
}