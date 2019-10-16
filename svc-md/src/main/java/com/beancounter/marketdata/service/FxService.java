package com.beancounter.marketdata.service;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.utils.RateCalculator;
import com.beancounter.marketdata.providers.fxrates.EcbService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FxService {
  private CurrencyService currencyService;
  private EcbService ecbService;
  private RateCalculator rateCalculator = new RateCalculator();
  // Persist!
  private Map<String, Collection<FxRate>> rateStore = new HashMap<>();

  @Autowired
  FxService(EcbService ecbService, CurrencyService currencyService) {
    this.ecbService = ecbService;
    this.currencyService = currencyService;
  }

  public FxResponse getRates(FxRequest fxRequest) {
    verify(fxRequest.getPairs());

    Collection<FxRate> rates = rateStore.get(fxRequest.getRateDate());
    if (rates == null) {
      rates = ecbService.getRates(fxRequest.getRateDate());
      rateStore.put(fxRequest.getRateDate(), rates);
    }
    Map<String, FxRate> mappedRates = new HashMap<>();
    for (FxRate rate : rates) {
      mappedRates.put(rate.getTo().getCode(), rate);
    }
    return FxResponse.builder().data(
        rateCalculator.compute(fxRequest.getRateDate(), fxRequest.getPairs(), mappedRates))
        .build();
  }

  private void verify(Collection<CurrencyPair> currencyPairs) {
    Collection<String> invalid = new ArrayList<>();
    for (CurrencyPair currencyPair : currencyPairs) {
      if (currencyService.getCode(currencyPair.getFrom()) == null) {
        invalid.add(currencyPair.getFrom());
      }
      if (currencyService.getCode(currencyPair.getTo()) == null) {
        invalid.add(currencyPair.getTo());
      }

    }
    if (!invalid.isEmpty()) {
      throw new BusinessException(String.format("Unsupported currencies in the request %s",
          String.join(",", invalid)));
    }

  }
}
