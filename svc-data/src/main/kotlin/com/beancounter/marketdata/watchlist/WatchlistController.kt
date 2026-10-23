package com.beancounter.marketdata.watchlist

import com.beancounter.auth.model.AuthConstants
import com.beancounter.marketdata.registration.SystemUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for the caller's watchlist: assets they watch, with no quantity. Each user
 * has one watchlist. Every item lookup is owner-scoped; another user's item answers 404.
 */
@RestController
@RequestMapping("/watchlist")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(name = "Watchlist", description = "Assets to watch, with price changes since added")
class WatchlistController(
    private val watchlistService: WatchlistService,
    private val watchlistAlertService: WatchlistAlertService,
    private val systemUserService: SystemUserService
) {
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_WIDGET}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get the caller's watchlist",
        description = """
            Items carry the latest stored close, its date, the change on day, the change since
            added and the distance to target. Amounts are in the asset's market currency and on
            today's share basis (addedPrice and targetPrice are rebased across later splits).
            Percentages are fractions (0.25 = 25%). Price-derived fields are null when the asset
            has no price; toTarget is (target - price) / price and null without a target. An
            empty watchlist returns no items.
        """
    )
    fun get(): WatchlistResponse = WatchlistResponse(watchlistService.get(systemUserService.getOrThrow()))

    @PostMapping(
        value = ["/items"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Add an asset to the watchlist",
        description = """
            When addedPrice is absent the current close is fetched through the provider-aware
            price path and stamped; 400 when no price is available and none was supplied.
            addedOn is today. 409 when the asset is already watched.
        """
    )
    fun addItem(
        @RequestBody request: AddWatchlistItemRequest
    ): WatchlistItemResponse = WatchlistItemResponse(watchlistService.addItem(systemUserService.getOrThrow(), request))

    @PatchMapping(
        value = ["/items/{itemId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Edit a watchlist item",
        description = """
            Absent fields are unchanged. Prices are taken on today's share basis. targetPrice
            sets the target; clearTarget = true removes it. Sending both answers 400.
        """
    )
    fun updateItem(
        @PathVariable itemId: String,
        @RequestBody request: UpdateWatchlistItemRequest
    ): WatchlistItemResponse =
        WatchlistItemResponse(watchlistService.updateItem(systemUserService.getOrThrow(), itemId, request))

    @DeleteMapping(value = ["/items/{itemId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "Remove an asset from the watchlist")
    fun deleteItem(
        @PathVariable itemId: String
    ): Map<String, String> {
        watchlistService.deleteItem(systemUserService.getOrThrow(), itemId)
        return mapOf("deleted" to itemId)
    }

    @GetMapping(value = ["/alerts"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_WIDGET}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
    @Operation(
        summary = "Get the caller's watchlist alerts",
        description = """
            Targets reached by a scheduled close, newest first. A target fires once, in the
            direction fixed when it was set (ABOVE: close at or above the target; BELOW: at or
            below); editing the target re-arms it. targetPrice and close are in the asset's market
            currency and on today's share basis. priceDate is the reaching close's date.
        """
    )
    fun alerts(): WatchlistAlertsResponse =
        WatchlistAlertsResponse(watchlistAlertService.alerts(systemUserService.getOrThrow()))

    @DeleteMapping(value = ["/alerts/{alertId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Dismiss a watchlist alert",
        description = "Returns the caller's remaining alerts. 404 when the alert is not the caller's."
    )
    fun deleteAlert(
        @PathVariable alertId: String
    ): WatchlistAlertsResponse =
        WatchlistAlertsResponse(watchlistAlertService.deleteAlert(systemUserService.getOrThrow(), alertId))
}