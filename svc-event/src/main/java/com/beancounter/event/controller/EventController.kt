package com.beancounter.event.controller

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventsResponse
import com.beancounter.event.service.EventService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * WebMVC interface to corporate actions
 */
@RestController
@RequestMapping
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class EventController(private val eventService: EventService) {
    @PostMapping(value = ["/backfill/{portfolioId}/{valuationDate}"], produces = ["application/json"])
    @ResponseStatus(HttpStatus.ACCEPTED)
    operator fun get(
        @PathVariable portfolioId: String,
        @PathVariable(required = false) valuationDate: String = DateUtils.today
    ) {
        eventService.backFillEvents(portfolioId, valuationDate)
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

    @GetMapping(value = ["/scheduled/{date}"], produces = ["application/json"])
    fun getScheduledEvents(@PathVariable date: String): CorporateEventsResponse {
        return eventService.getScheduledEvents(DateUtils().getDate(date))
    }
}
