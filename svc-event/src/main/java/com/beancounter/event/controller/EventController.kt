package com.beancounter.event.controller

import com.beancounter.auth.server.RoleHelper
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventsResponse
import com.beancounter.event.service.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
class EventController(private val eventService: EventService) {
    @PostMapping(value = ["/backfill/{portfolioCode}/{valuationDate}"], produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    operator fun get(@PathVariable portfolioCode: String,
                     @PathVariable(required = false) valuationDate: String = "today") {
        eventService.backFillEvents(portfolioCode, valuationDate)
    }

    @GetMapping(value = ["/{id}"], produces = ["application/json"])
    fun getEvent(@PathVariable id: String): CorporateEventResponse {
        return eventService[id]
    }

    @PostMapping(value = ["/{id}"], produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reprocess(@PathVariable id: String): CorporateEventResponse {
        val corporateEventResponse = eventService[id]
        eventService.processMessage(corporateEventResponse.data)
        return corporateEventResponse
    }

    @GetMapping(value = ["/asset/{id}"], produces = ["application/json"])
    fun getAssetEvents(@PathVariable id: String): CorporateEventsResponse {
        return eventService.getAssetEvents(id)
    }


}