package com.beancounter.event.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.event.contract.CorporateEventResponse;
import com.beancounter.event.contract.CorporateEventsResponse;
import com.beancounter.event.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Slf4j
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
public class EventController {
  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @PostMapping(value = "/backfill/{portfolioCode}/{valuationDate}", produces = "application/json")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void get(@PathVariable String portfolioCode,
           @PathVariable(required = false) String valuationDate) {
    eventService.backFillEvents(portfolioCode, valuationDate);

  }

  @GetMapping(value = "/{id}", produces = "application/json")
  CorporateEventResponse getEvent(@PathVariable String id) {
    CorporateEventResponse corporateEventResponse = eventService.get(id);
    if (corporateEventResponse == null) {
      throw new BusinessException(String.format("Corporate Event %s not found", id));
    }
    return corporateEventResponse;
  }

  @PostMapping(value = "/{id}", produces = "application/json")
  CorporateEventResponse reprocess(@PathVariable String id) {
    CorporateEventResponse corporateEventResponse = eventService.get(id);
    if (corporateEventResponse == null) {
      throw new BusinessException(String.format("Corporate Event %s not found", id));
    }
    eventService.processMessage(corporateEventResponse.getData());
    return corporateEventResponse;
  }

  @GetMapping(value = "/asset/{id}", produces = "application/json")
  CorporateEventsResponse getAssetEvents(@PathVariable String id) {
    return eventService.getAssetEvents(id);
  }

}
