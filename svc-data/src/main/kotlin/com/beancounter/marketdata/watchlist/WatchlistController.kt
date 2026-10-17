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
 * REST controller for watchlists: caller-owned, named lists of assets with no quantity.
 * Every endpoint is owner-scoped; another user's watchlist answers 404.
 */
@RestController
@RequestMapping("/watchlists")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(name = "Watchlists", description = "Named lists of assets to watch, with price changes since added")
class WatchlistController(
    private val watchlistService: WatchlistService,
    private val systemUserService: SystemUserService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "List the caller's watchlists with item counts")
    fun getWatchlists(): WatchlistsResponse = WatchlistsResponse(watchlistService.list(systemUserService.getOrThrow()))

    @PostMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Create a watchlist",
        description = "Code is upper-cased and must be unique for the caller (409 when taken)."
    )
    fun create(
        @RequestBody request: CreateWatchlistRequest
    ): WatchlistResponse = WatchlistResponse(watchlistService.create(systemUserService.getOrThrow(), request))

    @GetMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get a watchlist with its items",
        description = """
            Each item carries the latest close, its date, the change on day, the change since
            added and the distance to target. Amounts are in the asset's market currency;
            percentages are fractions (0.25 = 25%). Price-derived fields are null when the
            asset has no price; toTarget is (target - price) / price and null without a target.
        """
    )
    fun get(
        @PathVariable id: String
    ): WatchlistResponse = WatchlistResponse(watchlistService.get(systemUserService.getOrThrow(), id))

    @PatchMapping(
        value = ["/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Rename a watchlist",
        description = "Absent fields are unchanged. A code already in use answers 409."
    )
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateWatchlistRequest
    ): WatchlistResponse = WatchlistResponse(watchlistService.update(systemUserService.getOrThrow(), id, request))

    @DeleteMapping(value = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "Delete a watchlist and its items")
    fun delete(
        @PathVariable id: String
    ): Map<String, String> {
        watchlistService.delete(systemUserService.getOrThrow(), id)
        return mapOf("deleted" to id)
    }

    @PostMapping(
        value = ["/{id}/items"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Add an asset to a watchlist",
        description = """
            When addedPrice is absent the latest known close is stamped; 400 when the asset
            has no price and none was supplied. addedOn is today. 409 when the asset is
            already on the watchlist.
        """
    )
    fun addItem(
        @PathVariable id: String,
        @RequestBody request: AddWatchlistItemRequest
    ): WatchlistItemResponse =
        WatchlistItemResponse(watchlistService.addItem(systemUserService.getOrThrow(), id, request))

    @PatchMapping(
        value = ["/{id}/items/{itemId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Edit a watchlist item",
        description = """
            Absent fields are unchanged. targetPrice sets the target; clearTarget = true
            removes it. Sending both answers 400.
        """
    )
    fun updateItem(
        @PathVariable id: String,
        @PathVariable itemId: String,
        @RequestBody request: UpdateWatchlistItemRequest
    ): WatchlistItemResponse =
        WatchlistItemResponse(watchlistService.updateItem(systemUserService.getOrThrow(), id, itemId, request))

    @DeleteMapping(value = ["/{id}/items/{itemId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "Remove an asset from a watchlist")
    fun deleteItem(
        @PathVariable id: String,
        @PathVariable itemId: String
    ): Map<String, String> {
        watchlistService.deleteItem(systemUserService.getOrThrow(), id, itemId)
        return mapOf("deleted" to itemId)
    }
}