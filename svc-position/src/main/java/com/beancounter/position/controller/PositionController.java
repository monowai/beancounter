package com.beancounter.position.controller;

import com.beancounter.auth.RoleHelper;
import com.beancounter.client.PortfolioService;
import com.beancounter.client.TrnService;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Still thinking on this.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping
@Slf4j
@CrossOrigin("*")
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class PositionController {

  private PositionService positionService;
  private Valuation valuationService;
  private PortfolioService portfolioService;
  private TrnService trnService;

  @Autowired
  PositionController(PositionService positionService,
                     PortfolioService portfolioService,
                     TrnService trnService) {
    this.positionService = positionService;
    this.portfolioService = portfolioService;
    this.trnService = trnService;
  }

  @Autowired
  void setValuationService(Valuation valuationService) {
    this.valuationService = valuationService;
  }


  @PostMapping
  PositionResponse computePositions(@RequestBody PositionRequest positionRequest) {
    return positionService.build(positionRequest);
  }


  @GetMapping(value = "/{code}/{valuationDate}", produces = "application/json")
  PositionResponse get(final @AuthenticationPrincipal Jwt jwt,
                       @PathVariable String code,
                       @PathVariable(required = false) String valuationDate) {
    Portfolio portfolio = portfolioService.getPortfolioByCode(code);
    TrnResponse trnResponse = trnService.read(portfolio);
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId(portfolio.getId())
        .trns(trnResponse.getData())
        .build();
    PositionResponse positionResponse = positionService.build(portfolio, positionRequest);
    if (valuationDate != null && !valuationDate.equalsIgnoreCase("today")) {
      positionResponse.getData().setAsAt(valuationDate);
    }
    return valuationService.value(positionResponse.getData());
  }
}