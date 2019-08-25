package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.marketdata.providers.fxrates.EcbService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FxService {
  private CurrencyService currencyService;
  private EcbService ecbService;
  private RateCalculator rateCalculator;
  // Persist!
  private Map<Date, Collection<FxRate>> rateStore = new HashMap<>();

  @Autowired
  FxService(EcbService ecbService, CurrencyService currencyService, RateCalculator rateCalculator) {
    this.ecbService = ecbService;
    this.currencyService = currencyService;
    this.rateCalculator = rateCalculator;
  }

  public Map<Date, Map<CurrencyPair, FxRate>> getRates(@NotNull Date asAt,
                                                       @NotNull Collection<CurrencyPair> currencyPairs) {
    verify(currencyPairs);

    Collection<FxRate> rates = rateStore.get(asAt);
    if (rates == null) {
      rates = ecbService.getRates(asAt);
      rateStore.put(asAt, rates);
    }
    Map<String, FxRate> mappedRates = new HashMap<>();
    for (FxRate rate : rates) {
      mappedRates.put(rate.getTo().getCode(), rate);
    }
    return rateCalculator.compute(asAt, currencyPairs, mappedRates);
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
