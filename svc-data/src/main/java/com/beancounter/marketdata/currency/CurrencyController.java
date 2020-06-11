package com.beancounter.marketdata.currency;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.CurrencyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@RequestMapping("/currencies")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class CurrencyController {

  private CurrencyService currencyService;

  @Autowired
  void setCurrencyService(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  @GetMapping
  CurrencyResponse getCurrencies() {
    return CurrencyResponse.builder()
        .data(currencyService.getCurrencies())
        .build();
  }
}
