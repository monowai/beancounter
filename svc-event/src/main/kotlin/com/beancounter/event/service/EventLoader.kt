package com.beancounter.event.service

import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.MarketData.Companion.isSplit
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.event.config.EventLoaderConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Loads corporate events for portfolios.
 */
@Service
class EventLoader(
    config: EventLoaderConfig,
    @Value("\${events.lookback-days:7}")
    private val lookbackDays: Long = 7
) {
    private val portfolioService = config.sharedConfig.portfolioService
    private val positionService = config.serviceConfig.positionService
    private val eventService = config.serviceConfig.eventService
    private val priceService = config.serviceConfig.priceService
    private val backFillService = config.serviceConfig.backFillService
    private val dateSplitter = config.authConfig.dateSplitter
    private val loginService = config.authConfig.loginService

    // private val authContext: OpenIdResponse = loginService.loginM2m()

    fun loadEvents(date: String) {
        val portfolios = portfolioService.portfolios
        log.info(
            "Loading missing events from date: $date (lookback: $lookbackDays days) for ${portfolios.data.size} portfolios"
        )
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
        val authContext = loginService.loginM2m()
        loginService.setAuthContext(authContext)
        runBlocking {
            val dates = dateSplitter.dateRange(date, "today")
            log.info("Loading missing events from date: $dates, portfolio: ${portfolio.code}/$portfolioId")
            for (processDate in dates) {
                launch {
                    val events = loadEvents(portfolio, processDate, authContext)
                    if (events > 0) {
                        log.trace("Loaded $events events for portfolio: ${portfolio.code}/$portfolioId")
                    }
                }
            }
        }
    }

    private fun loadEvents(
        portfolio: Portfolio,
        date: LocalDate,
        authContext: OpenIdResponse
    ): Int {
        loginService.setAuthContext(authContext)
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
                    val events = load(position, date, authContext)
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
        }

        return totalEvents
    }

    private fun load(
        position: Position,
        date: LocalDate,
        authContext: OpenIdResponse
    ): Int {
        var eventCount = 0
        if (positionService.includePosition(position)) {
            loginService.setAuthContext(authContext)
            val events = priceService.getEvents(position.asset.id)
            val lookbackStart = date.minusDays(lookbackDays)
            for (priceResponse in events.data) {
                val eventDate = priceResponse.priceDate
                // Check if event falls within the lookback window (lookbackStart to date inclusive)
                if (!eventDate.isBefore(lookbackStart) && !eventDate.isAfter(date)) {
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