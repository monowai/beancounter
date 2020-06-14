package com.beancounter.marketdata.assets;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.marketdata.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/assets")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class AssetController {

  private final AssetService assetService;
  private final MarketDataService marketDataService;

  @Autowired
  AssetController(AssetService assetService, MarketDataService marketDataService) {
    this.assetService = assetService;
    this.marketDataService = marketDataService;
  }

  @GetMapping(value = "/{market}/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  AssetResponse getAsset(@PathVariable String market, @PathVariable String code) {
    return AssetResponse.builder().data(assetService.find(market, code)).build();
  }

  @GetMapping(value = "/{assetId}")
  AssetResponse getAsset(@PathVariable String assetId) {
    return AssetResponse.builder().data(assetService.find(assetId)).build();
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  AssetUpdateResponse update(@RequestBody AssetRequest assetRequest) {
    return assetService.process(assetRequest);
  }

  @PostMapping(value = "/{assetId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void backFill(@PathVariable String assetId) {
    marketDataService.backFill(assetService.find(assetId));
  }

}
