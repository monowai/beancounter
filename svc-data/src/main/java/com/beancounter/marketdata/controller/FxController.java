package com.beancounter.marketdata.controller;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.marketdata.service.FxRateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@RequestMapping("/fx")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
public class FxController {

  private FxRateService fxRateService;

  @Autowired
  FxController(FxRateService fxRateService) {
    this.fxRateService = fxRateService;
  }

  @PostMapping
  FxResponse getRates(@RequestBody FxRequest fxRequest) {
    return fxRateService.getRates(fxRequest);
  }

}
