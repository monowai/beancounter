package com.beancounter.marketdata.controller;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.service.MarketDataService;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/price")
public class PriceController {

  private MarketDataService marketDataService;

  @Autowired
  PriceController(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  /**
   * Market:Asset i.e. NYSE:MSFT.
   *
   * @param assetId Exchange:Code
   * @return Market Dat information for the supplied asset
   */
  @GetMapping(value = "/{marketId}/{assetId}", produces = "application/json")
  MarketData getPrice(@PathVariable("marketId") String marketId,
                      @PathVariable("assetId") String assetId) {
    Asset testAsset = Asset.builder()
        .code(assetId)
        .market(Market.builder().code(marketId).build())
        .build();
    try {
      return marketDataService.getCurrent(testAsset);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }

  }

  @PostMapping
  Collection<MarketData> getPrices(@RequestBody Collection<Asset> assets) {
    try {
      return marketDataService.getCurrent(assets);
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }

  }

}
