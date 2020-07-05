package com.beancounter.position.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Positions;
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
import org.springframework.web.bind.annotation.RequestParam;
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
@PreAuthorize(value = "hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
public class PositionController {

  private Valuation valuationService;
  private final PortfolioServiceClient portfolioServiceClient;

  @Autowired
  PositionController(PositionService positionService,
                     PortfolioServiceClient portfolioServiceClient) {
    this.portfolioServiceClient = portfolioServiceClient;
  }

  @Autowired
  void setValuationService(Valuation valuationService) {
    this.valuationService = valuationService;
  }

  @GetMapping(value = "/{code}/{valuationDate}", produces = "application/json")
  PositionResponse get(@PathVariable String code,
                       @PathVariable(required = false) String valuationDate,
                       @RequestParam(value = "value", defaultValue = "true") boolean value) {
    Portfolio portfolio = portfolioServiceClient.getPortfolioByCode(code);
    Positions positions = valuationService.build(portfolio, valuationDate).getData();
    if (value) {
      return valuationService.value(positions);
    }
    return new PositionResponse(positions);
  }

  @PostMapping(value = "/query",
      consumes = "application/json",
      produces = "application/json")
  PositionResponse query(@RequestBody TrustedTrnQuery trnQuery) {
    return valuationService.build(trnQuery);
  }

}