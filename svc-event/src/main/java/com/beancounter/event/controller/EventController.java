package com.beancounter.event.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.event.service.PositionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Slf4j
@CrossOrigin("*")
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
public class EventController {
  private final PositionService positionService;

  public EventController(PositionService positionService) {
    this.positionService = positionService;
  }

  @GetMapping(value = "/{code}/{valuationDate}", produces = "application/json")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void get(@PathVariable String code,
                       @PathVariable(required = false) String valuationDate) {
    positionService.backFillEvents(code, valuationDate);
  }

}
