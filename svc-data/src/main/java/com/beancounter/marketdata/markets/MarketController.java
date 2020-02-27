package com.beancounter.marketdata.markets;

import com.beancounter.auth.RoleHelper;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Market;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/markets")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class MarketController {

  private MarketService marketService;

  @Autowired
  MarketController(MarketService marketService) {
    this.marketService = marketService;
  }

  @GetMapping
  MarketResponse getMarkets() {
    return marketService.getMarkets();
  }

  @GetMapping(value = "/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  MarketResponse getMarket(@PathVariable String code) {
    MarketResponse marketResponse = MarketResponse.builder().build();
    Market market = marketService.getMarket(code);
    if (market == null) {
      throw new BusinessException(String.format("No market could be found for %s", code));
    }
    marketResponse.setData(Collections.singleton(market));
    return marketResponse;
  }

}
