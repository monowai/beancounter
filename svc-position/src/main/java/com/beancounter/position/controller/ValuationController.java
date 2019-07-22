package com.beancounter.position.controller;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Valuation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/value")
public class ValuationController {

  private Valuation valuationService;

  @Autowired
  ValuationController(Valuation valuationService) {
    this.valuationService = valuationService;
  }

  @PostMapping()
  Positions getPositions(@RequestBody Positions positions) {
    try {
      return valuationService.value(positions);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }
  }

}