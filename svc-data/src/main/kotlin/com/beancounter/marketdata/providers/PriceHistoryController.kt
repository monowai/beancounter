package com.beancounter.marketdata.providers

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.EnsureHistoryRequest
import com.beancounter.common.contracts.EnsureHistoryResponse
import com.beancounter.common.contracts.PriceHistoryResponse
import com.beancounter.common.contracts.RepairSplitsResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.providers.MarketDataPriceProvider.Companion.MAX_BACKFILL_YEARS
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Stored price history: reading it, filling its gaps from providers, and repairing its splits.
 */
@RestController
@RequestMapping("/prices")
@PreAuthorize(
    AuthConstants.USER_OR_SYSTEM
)
@Tag(
    name = "Price Management",
    description = "Operations for retrieving and managing market prices for financial assets"
)
class PriceHistoryController(
    private val marketDataService: MarketDataService,
    private val priceHistoryReader: PriceHistoryReader,
    private val priceSplits: PriceSplits,
    private val dateUtils: DateUtils,
    private val portfolioPriceBackfillService: PortfolioPriceBackfillService,
    private val priceBackfillCoordinator: PriceBackfillCoordinator
) {
    private val log = LoggerFactory.getLogger(PriceHistoryController::class.java)

    @GetMapping("/{assetId}/history")
    @Operation(
        summary = "Get price history for an asset between two dates",
        description = """
            Retrieves daily closing prices for an asset between the supplied dates (inclusive),
            sorted by price date ascending. If the asset has no persisted price history we try
            once to backfill from the configured external provider so newly-researched assets
            return data on first chart open. A cached series that falls short of the requested
            window — at either end — is returned as-is and an async backfill is scheduled, so
            the next call serves the extended range. Returns the hydrated asset together with a
            collection of price points.
        """
    )
    fun getPriceHistory(
        @PathVariable assetId: String,
        @RequestParam from: String,
        @RequestParam(required = false) to: String = TODAY
    ): PriceHistoryResponse {
        val fromDate = dateUtils.getFormattedDate(from)
        val toDate = dateUtils.getFormattedDate(to)
        val initial = priceHistoryReader.getPriceHistory(assetId, fromDate, toDate)
        val targetFrom = fromDate.coerceAtLeast(dateUtils.date.minusYears(MAX_BACKFILL_YEARS))

        if (initial.prices.isEmpty()) {
            // Nothing cached at all — block briefly so the chart isn't empty.
            return try {
                marketDataService.backFill(assetId, targetFrom)
                priceHistoryReader.getPriceHistory(assetId, fromDate, toDate)
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                log.warn("Price history backfill failed for asset {}", assetId, e)
                initial
            }
        }

        // We have something to render. If the cached range falls short of what
        // was asked for — at either end, or with a hole in the middle — schedule
        // an async backfill so the next request can return the complete history
        // without paying provider latency now.
        val dates = initial.prices.map { it.priceDate }
        val earliest = dates.minOrNull()
        val latest = dates.maxOrNull()
        val shortAtStart = earliest != null && earliest > targetFrom.plusDays(BACKFILL_RETRY_BUFFER_DAYS)
        val hasHole = PriceSeriesGaps.firstGapStart(dates) != null
        if (shortAtStart || hasHole || staleTail(latest, toDate)) {
            priceBackfillCoordinator.scheduleBackfill(assetId, targetFrom)
        }
        return initial
    }

    /**
     * True when the cached series stops well short of the end of the requested
     * window. The daily refresh only prices held assets and INDEX benchmarks, so
     * anything else — a chart overlay's legs, a ticker researched but never
     * bought — keeps whatever tail it was last backfilled with, forever. The
     * leading-edge check above cannot see that: its earliest date is perfect.
     *
     * Tolerance covers a normal non-trading stretch (Friday's close read on a
     * Tuesday after a Monday holiday) so routine chart opens don't spend a
     * provider call per asset per cooldown window on a series that is already
     * current.
     */
    private fun staleTail(
        latest: LocalDate?,
        toDate: LocalDate
    ): Boolean {
        if (latest == null) return false
        val windowEnd = minOf(toDate, dateUtils.date)
        return latest < windowEnd.minusDays(STALE_TAIL_BUFFER_DAYS)
    }

    private companion object {
        // Headroom buffer so we don't re-trigger backfills when the cached
        // earliest already sits right at the provider's reachable floor.
        const val BACKFILL_RETRY_BUFFER_DAYS = 7L

        // Trading days the tail may lag the window end before we call it stale.
        const val STALE_TAIL_BUFFER_DAYS = 5L
    }

    @PostMapping("/bulk")
    @Operation(
        summary = "Get prices for multiple assets across multiple dates",
        description = """
            Retrieves prices for multiple assets across multiple dates in a single call.
            DB-only — does not trigger external provider calls.
            For dates without exact matches (weekends/holidays), returns the nearest prior price.
        """
    )
    fun getBulkPrices(
        @RequestBody bulkPriceRequest: BulkPriceRequest
    ): BulkPriceResponse = marketDataService.getBulkAssetPrices(bulkPriceRequest)

    @PostMapping(
        value = ["/{assetId}/events"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Backfill events for asset",
        description = """
            Initiates a backfill of corporate events for a specific asset.
            This is an asynchronous operation that processes historical events.

            Use this to:
            * Populate missing corporate event data
            * Refresh event history for an asset
            * Ensure complete event data coverage
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Backfill operation accepted and started"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found"
            )
        ]
    )
    fun backFill(
        @Parameter(
            description = "Asset identifier for event backfill",
            example = "asset-123"
        )
        @PathVariable assetId: String
    ) = marketDataService.backFill(assetId)

    @PostMapping("/ensure-history")
    @Operation(
        summary = "Ensure price history coverage for a set of assets",
        description = """
            Fire-and-forget hook used by performance / growth views to nudge
            deep price-history backfills. For each asset, the coordinator
            schedules an async backfill from the supplied fromDate when the
            cached earliest is later — concurrent calls are deduplicated and
            failed backfills back off via a 1h cooldown.
        """
    )
    fun ensureHistory(
        @RequestBody request: EnsureHistoryRequest
    ): EnsureHistoryResponse {
        val targetFrom = request.fromDate.coerceAtLeast(dateUtils.date.minusYears(MAX_BACKFILL_YEARS))
        // Trim and de-duplicate so a sloppy caller (e.g. a portfolio with the same asset across
        // currencies) doesn't blow the per-asset cooldown budget on phantom IDs, and so the
        // returned `scheduled` count reflects what we actually queued.
        val cleanAssetIds =
            request.assetIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        // Count what the coordinator actually accepted — cooldowns, in-flight
        // duplicates, and queue-saturation drops all return false. Reporting
        // the post-filter count lets the caller see real backpressure instead
        // of trusting an always-equal `scheduled = requested` number.
        val scheduled =
            cleanAssetIds.count { assetId ->
                priceBackfillCoordinator.scheduleBackfill(assetId, targetFrom)
            }
        return EnsureHistoryResponse(scheduled = scheduled)
    }

    @PostMapping("/backfill/{code}")
    @Operation(
        summary = "Backfill prices for a portfolio's historical dates",
        description = """
            Pre-populates the price database for all assets in a portfolio across
            monthly intervals and external cash flow dates. This ensures subsequent
            performance calculations are fast.
        """
    )
    fun backfillPortfolio(
        @Parameter(
            description = "Portfolio code",
            example = "USV"
        )
        @PathVariable code: String
    ): Map<String, Any> = portfolioPriceBackfillService.backfill(code)

    @PostMapping("/{assetId}/repair-splits")
    @PreAuthorize(
        "hasAnyAuthority('" + AuthConstants.SCOPE_ADMIN + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
    )
    @Operation(
        summary = "Stamp the split column on existing rows from provider events",
        description = """
            Walks corporate-event splits for the asset (via EventServiceFacade so each market routes
            to its configured provider) and stamps the `split` column on each ex-date MarketData row
            that still carries the default `1`. After repair, SplitAdjuster rebases historical OHLC
            via column-transition detection on every read path without re-querying the provider per
            call. Idempotent. Restricted to admin / system scope — write operation on shared
            historical data.
        """
    )
    fun repairSplits(
        @Parameter(
            description = "Internal asset identifier",
            example = "asset-123"
        )
        @PathVariable assetId: String
    ): RepairSplitsResponse = priceSplits.repairSplits(assetId)
}