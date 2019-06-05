package com.beancounter.position.controller;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.PositionService;
import com.beancounter.position.service.Valuation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Still thinking on this.
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping("/")
@Slf4j
public class PositionController {

  private PositionService positionService;
  private Valuation valuationService;

  @Autowired
  PositionController(PositionService positionService, Valuation valuationService) {
    this.positionService = positionService;
    this.valuationService = valuationService;
  }

  @PostMapping()
  Positions computePositions(@RequestBody Collection<Transaction> transactions) {
    try {
      return positionService.computePositions(transactions);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }
  }


  @GetMapping(value = "/{assetId}", produces = "application/json")
  MarketData getPrice(@PathVariable("assetId") String assetId) {
    try {
      return valuationService.getPrice(assetId);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }
  }

  @GetMapping(value = "/test", produces = "application/json")
  @CrossOrigin
  Map<String,Object> getTest() throws IOException {
    try {
      ObjectMapper mapper = new ObjectMapper();
      MapType javaType = mapper.getTypeFactory()
          .constructMapType(SortedMap.class, String.class, Object.class);
      File jsonFile = new ClassPathResource("holdings.json").getFile();
      return mapper.readValue(jsonFile, javaType);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }
  }
}