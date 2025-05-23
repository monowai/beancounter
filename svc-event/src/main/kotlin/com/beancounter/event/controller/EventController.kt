package com.beancounter.event.controller

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventLoader
import com.beancounter.event.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class EventController(
    private val eventService: EventService,
    private val backFillService: BackFillService,
    private val eventLoader: EventLoader,
    private val portfolioService: PortfolioServiceClient,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @PostMapping(
        value = ["/backfill/{portfolioId}/{fromDate}/{toDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    operator fun get(
        @PathVariable portfolioId: String,
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY,
        @PathVariable(required = false) toDate: String = DateUtils.TODAY
    ) = backFillService.backFillEvents(
        portfolioId,
        fromDate,
        toDate
    )

    @PostMapping(
        value = ["/backfill/{fromDate}/{toDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun backFillPortfolios(
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY,
        @PathVariable(required = false) toDate: String = DateUtils.TODAY
    ) {
        eventLoader.loadEvents(fromDate)
        val portfolios = portfolioService.portfolios
        log.info("Backfill events for portfolioCount: ${portfolios.data.size}, from: $fromDate, to: $toDate")
        for (portfolio in portfolios.data) {
            log.info("BackFilling ${portfolio.code}, $fromDate to $toDate")
            backFillService.backFillEvents(
                portfolio.id,
                fromDate,
                toDate
            )
        }
    }

    @PostMapping(
        value = ["/load/{fromDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun loadEvents(
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY
    ) = eventLoader.loadEvents(fromDate)

    @PostMapping(
        value = ["/load/{portfolioId}/{asAtDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun loadPortfolioEvents(
        @PathVariable(required = false) asAtDate: String = DateUtils.TODAY,
        @PathVariable portfolioId: String
    ) {
        eventLoader.loadEvents(
            portfolioId,
            asAtDate
        )
    }

    @GetMapping(
        value = ["/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getEvent(
        @PathVariable id: String
    ): CorporateEventResponse = eventService[id]

    @PostMapping(
        value = ["/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reprocess(
        @PathVariable id: String
    ): CorporateEventResponse {
        val corporateEventResponse = eventService[id]
        eventService.processEvent(corporateEventResponse.data)
        return corporateEventResponse
    }

    @GetMapping(
        value = ["/asset/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getAssetEvents(
        @PathVariable id: String
    ): CorporateEventResponses = eventService.getAssetEvents(id)

    @GetMapping(
        value = ["/scheduled/{date}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getScheduledEvents(
        @PathVariable date: String
    ): CorporateEventResponses = eventService.getScheduledEvents(dateUtils.getFormattedDate(date))
}