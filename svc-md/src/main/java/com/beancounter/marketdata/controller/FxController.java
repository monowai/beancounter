package com.beancounter.marketdata.controller;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.request.FxRequest;
import com.beancounter.marketdata.service.FxService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/fx")
public class FxController {

  private FxService fxService;

  @Autowired
  FxController(FxService fxService) {
    this.fxService = fxService;
  }

  @PostMapping
  Map<CurrencyPair, FxRate> getRates(@RequestBody FxRequest fxRequest) {
    try {
      return fxService.getRates(fxRequest.getRateDate(), fxRequest.getPairs());
    } catch (BusinessException be) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, be.getMessage(), be);
    }

  }

}
