package com.beancounter.event.backfill

import com.beancounter.auth.client.LoginService
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.corporate.CorporateEventStore
import com.beancounter.event.distribution.EventDistribution
import com.beancounter.event.distribution.PositionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Loads and processes a portfolio's corporate events from a date to today in one pass:
 * the positions are read once, each held asset's events are fetched once and those in the
 * range stored, then every stored event in the range is processed. The day-by-day
 * [EventLoader] fetches each asset's events and the positions again for every date, which
 * over years is tens of thousands of calls; this is one call per asset.
 *
 * Positions are read as of today, so an asset sold before today is not loaded.
 */
@Service
class RangeEventLoader(
    private val positionService: PositionService,
    private val assetEventLoader: AssetEventLoader,
    private val eventStore: CorporateEventStore,
    private val eventDistribution: EventDistribution,
    private val loginService: LoginService,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(RangeEventLoader::class.java)

    /**
     * Returns the number of events processed. A long load can outlive the M2M token, so each
     * call is retried once with a fresh token rather than failing every asset after expiry.
     */
    fun load(
        portfolio: Portfolio,
        from: LocalDate
    ): Int {
        val today = dateUtils.date
        val assetIds =
            loginService.retryOnJwtExpiry {
                loginService.setAuthContext(loginService.loginM2m())
                positionService
                    .getPositions(portfolio, today.toString())
                    .data.positions.values
                    .filter { positionService.includePosition(it) }
                    .map { it.asset.id }
                    .distinct()
            }
        log.info("Loading events from {} for {} assets, portfolio: {}", from, assetIds.size, portfolio.code)
        assetIds.forEach { assetId ->
            runCatching { loginService.retryOnJwtExpiry { assetEventLoader.loadBetween(assetId, from, today) } }
                .onFailure { log.warn("Event load failed, asset: {}, from: {}", assetId, from, it) }
        }
        val events = assetIds.flatMap { eventStore.findInRange(it, from, today) }
        events.forEach { event ->
            runCatching { loginService.retryOnJwtExpiry { eventDistribution.processEvent(event) } }
                .onFailure { log.warn("Event processing failed, event: {}", event.id, it) }
        }
        log.info("Processed {} events from {}, portfolio: {}", events.size, from, portfolio.code)
        return events.size
    }
}