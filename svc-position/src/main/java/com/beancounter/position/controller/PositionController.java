package com.beancounter.position.controller;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.service.BcService;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
  private ObjectMapper mapper = new ObjectMapper();


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
  @Deprecated // Dev use
  PositionResponse get(@PathVariable String portfolioCode) throws IOException {
    // Currently no persistence. This is an emulated flow
    File tradeFile = new ClassPathResource(portfolioCode + ".json").getFile();
    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Transaction.class);
    Portfolio portfolio = bcService.getPortfolioByCode(portfolioCode);
    Collection<Transaction> results = mapper.readValue(tradeFile, javaType);
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId(portfolioCode)
        .transactions(results)
        .build();
    PositionResponse positionResponse = positionService.build(portfolio, positionRequest);
    return valuationService.value(positionResponse.getData());
  }
}