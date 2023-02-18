package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * loads and stores events from the Market Data service.
 */
@Service
class EventLoader(
    private val portfolioService: PortfolioServiceClient,
    private val positionService: PositionService,
    private val priceService: PriceService,
    private val eventService: EventService,
    private val loginService: LoginService
) {
    fun loadEvents(portfolioId: String, date: LocalDate) {
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        loadEvents(portfolio, date)
    }

    fun loadEvents(date: LocalDate) {
        loginService.login() // m2m login
        val portfolios = portfolioService.portfolios
        for (portfolio in portfolios.data) {
            loadEvents(portfolio, date)
        }
    }

    fun loadEvents(portfolio: Portfolio, date: LocalDate) {
        val positionResponse = positionService.getPositions(portfolio, date.toString())
        log.debug("Processing portfolio: $portfolio, positions: ${positionResponse.data.positions.size}")
        for (position in positionResponse.data.positions.values) {
            if (eventService.getAssetEvents(position.asset.id).data.isEmpty()) {
                // Not well thought through, should also consider date.
                load(position)
            }
        }
        log.debug("Completed... code: ${portfolio.code}, id: ${portfolio.id}")
    }

    private fun load(position: Position) {
        if (positionService.includePosition(position)) {
            log.debug("Processing events for ${position.asset.name}, ${position.asset.id}")
            val events = priceService.getEvents(position.asset.id)
            if (!events.data.isEmpty()) {
                log.info("Found ${events.data.size} events")
            }
            for (priceResponse in events.data) {
                val trnType = if (priceResponse.isSplit()) TrnType.SPLIT else TrnType.DIVI
                eventService.save(
                    CorporateEvent(
                        trnType = trnType,
                        recordDate = priceResponse.priceDate!!,
                        assetId = position.asset.id,
                        rate = priceResponse.dividend,
                        split = priceResponse.split
                    )
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
