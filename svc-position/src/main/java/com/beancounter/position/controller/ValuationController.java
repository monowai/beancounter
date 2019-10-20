package com.beancounter.position.controller;

import com.beancounter.position.model.Positions;
import com.beancounter.position.service.Valuation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping
  Positions value(@RequestBody Positions positions) {
    return valuationService.value(positions);
  }

}