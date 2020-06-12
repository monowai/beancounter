package com.beancounter.position.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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

  private final PositionService positionService;
  private Valuation valuationService;
  private final PortfolioServiceClient portfolioServiceClient;

  @Autowired
  PositionController(PositionService positionService,
                     PortfolioServiceClient portfolioServiceClient) {
    this.positionService = positionService;
    this.portfolioServiceClient = portfolioServiceClient;
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
  PositionResponse get(@PathVariable String code,
                       @PathVariable(required = false) String valuationDate) {
    Portfolio portfolio = portfolioServiceClient.getPortfolioByCode(code);
    return valuationService.value(
        valuationService.build(portfolio, valuationDate).getData());
  }

  @PostMapping(value = "/query",
      consumes = "application/json",
      produces = "application/json")
  PositionResponse query(@RequestBody TrustedTrnQuery trnQuery) {
    return valuationService.build(trnQuery);
  }

}