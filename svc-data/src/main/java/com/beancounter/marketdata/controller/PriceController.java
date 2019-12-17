package com.beancounter.marketdata.controller;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/prices")
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
  @GetMapping(value = "/{marketId}/{assetId}")
  PriceResponse getPrice(@PathVariable("marketId") String marketId,
                         @PathVariable("assetId") String assetId) {
    Asset testAsset = Asset.builder()
        .code(assetId)
        .id(assetId)
        .market(Market.builder()
            .id(marketId)
            .code(marketId).build())
        .build();
    return marketDataService.getPrice(testAsset);

  }

  @PostMapping
  PriceResponse getPrices(@RequestBody PriceRequest priceRequest) {
    return marketDataService.getPrice(priceRequest);
  }

}
