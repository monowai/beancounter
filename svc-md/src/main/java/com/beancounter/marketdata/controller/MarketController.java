package com.beancounter.marketdata.controller;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.marketdata.service.MarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/")
public class MarketController {

  private MarketService marketService;

  @Autowired
  MarketController(MarketService marketService) {
    this.marketService = marketService;
  }


  @GetMapping("/markets")
  MarketResponse getMarkets() {
    return marketService.getMarkets();
  }

}
