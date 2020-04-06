package com.beancounter.marketdata.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class PriceController {

  private MarketDataService marketDataService;
  private AssetService assetService;

  @Autowired
  PriceController(MarketDataService marketDataService, AssetService assetService) {
    this.marketDataService = marketDataService;
    this.assetService = assetService;
  }

  /**
   * Market:Asset i.e. NYSE:MSFT.
   *
   * @param assetCode Exchange:Code
   * @return Market Dat information for the supplied asset
   */
  @GetMapping(value = "/{marketCode}/{assetCode}")
  PriceResponse getPrice(@PathVariable("marketCode") String marketCode,
                         @PathVariable("assetCode") String assetCode) {
    Asset asset = assetService.find(marketCode, assetCode);
    if (asset == null) {
      throw new BusinessException(String.format("Asset not found %s/%s", marketCode, assetCode));
    }
    return marketDataService.getPrice(asset);

  }

  @PostMapping
  PriceResponse prices(@RequestBody PriceRequest priceRequest) {
    for (AssetInput requestedAsset : priceRequest.getAssets()) {
      Asset asset = assetService
          .find(requestedAsset.getMarket(), requestedAsset.getCode());
      if (asset != null) {
        requestedAsset.setResolvedAsset(asset);
      }

    }

    return marketDataService.getPrice(priceRequest);
  }

}
