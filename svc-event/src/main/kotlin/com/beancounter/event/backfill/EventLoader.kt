package com.beancounter.event.backfill

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.distribution.PositionService
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Loads corporate events for portfolios: for each date from the requested one to
 * today, stores every held asset's events, then backfills the portfolio's stored
 * events for that date once.
 */
@Service
class EventLoader(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val backFillService: BackFillService,
    private val dateSplitter: DateSplitter,
    private val loginService: LoginService,
    private val assetEventLoader: AssetEventLoader
) {
    fun loadEvents(date: String) {
        val portfolios = portfolioService.portfolios
        log.info("Loading events from date: $date for ${portfolios.data.size} portfolios")
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
        runBlockingTraced {
            val dates = dateSplitter.dateRange(date, DateUtils.TODAY)
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
        val positions =
            positionService
                .getPositions(portfolio, date.toString())
                .data.positions.values
        if (positions.isEmpty()) {
            return 0
        }
        val totalEvents =
            Executors.newFixedThreadPool(POSITION_LOAD_THREADS).use { executor ->
                executor
                    .invokeAll(positions.map { position -> Callable { loadContained(position, date, authContext) } })
                    .sumOf { it.get() }
            }
        if (totalEvents > 0) {
            backFillService.backFillEvents(portfolio.id, date.toString())
            log.debug("portfolio: ${portfolio.code}/${portfolio.id}, events: $totalEvents, date: $date")
        }
        return totalEvents
    }

    /** One asset's failure is logged and counts as no events; the other assets still load. */
    private fun loadContained(
        position: Position,
        date: LocalDate,
        authContext: OpenIdResponse
    ): Int =
        runCatching { load(position, date, authContext) }
            .onFailure { log.warn("Event load failed, asset: ${position.asset.id}, date: $date", it) }
            .getOrDefault(0)

    private fun load(
        position: Position,
        date: LocalDate,
        authContext: OpenIdResponse
    ): Int {
        if (!positionService.includePosition(position)) {
            return 0
        }
        loginService.setAuthContext(authContext)
        val eventCount = assetEventLoader.load(position.asset.id, date)
        if (eventCount != 0) {
            log.debug("Loaded events: $eventCount, asset: ${position.asset.id}, name: ${position.asset.name}")
        }
        return eventCount
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventLoader::class.java)
        private const val POSITION_LOAD_THREADS = 10
    }
}