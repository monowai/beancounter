package com.beancounter.position.controller;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.service.BcService;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@CrossOrigin
public class PositionController {

  private PositionService positionService;
  private Valuation valuationService;
  private BcService bcService;

  @Autowired
  PositionController(PositionService positionService, BcService bcService) {
    this.positionService = positionService;
    this.bcService = bcService;
  }

  @Autowired
  void setValuationService(Valuation valuationService) {
    this.valuationService = valuationService;
  }


  @PostMapping
  PositionResponse computePositions(@RequestBody PositionRequest positionRequest) {
    return positionService.build(positionRequest);
  }


  @GetMapping(value = "/{portfolioCode}", produces = "application/json")
  PositionResponse get(@PathVariable String portfolioCode) {
    // Currently no persistence. This is an emulated flow
    Portfolio portfolio = bcService.getPortfolioByCode(portfolioCode);
    TrnResponse trnResponse = bcService.getTrn(portfolio);
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId(portfolio.getId())
        .trns(trnResponse.getTrns())
        .build();
    PositionResponse positionResponse = positionService.build(portfolio, positionRequest);
    return valuationService.value(positionResponse.getData());
  }
}