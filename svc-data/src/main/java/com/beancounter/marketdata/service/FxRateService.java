package com.beancounter.marketdata.service;

import com.beancounter.client.FxService;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.RateCalculator;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.providers.fxrates.EcbService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class FxRateService implements FxService {
  private final CurrencyService currencyService;
  private final EcbService ecbService;
  private DateUtils dateUtils = new DateUtils();

  @Autowired
  FxRateService(EcbService ecbService, CurrencyService currencyService) {
    this.ecbService = ecbService;
    this.currencyService = currencyService;
  }

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  @Cacheable("fx.rates")
  public FxResponse getRates(FxRequest fxRequest) {
    verify(fxRequest.getPairs());
    Collection<FxRate> rates;

    String rateDate;
    if (fxRequest.getRateDate() == null) {
      // Looking for current
      rateDate = dateUtils.today();
    } else {
      rateDate = fxRequest.getRateDate();
    }

    rates = ecbService.getRates(rateDate);

    Map<String, FxRate> mappedRates = new HashMap<>();
    for (FxRate rate : rates) {
      mappedRates.put(rate.getTo().getCode(), rate);
    }
    return FxResponse.builder().data(
        RateCalculator.compute(rateDate, fxRequest.getPairs(), mappedRates))
        .build();
  }

  private void verify(Collection<IsoCurrencyPair> isoCurrencyPairs) {
    Collection<String> invalid = new ArrayList<>();
    for (IsoCurrencyPair isoCurrencyPair : isoCurrencyPairs) {
      if (currencyService.getCode(isoCurrencyPair.getFrom()) == null) {
        invalid.add(isoCurrencyPair.getFrom());
      }
      if (currencyService.getCode(isoCurrencyPair.getTo()) == null) {
        invalid.add(isoCurrencyPair.getTo());
      }

    }
    if (!invalid.isEmpty()) {
      throw new BusinessException(String.format("Unsupported currencies in the request %s",
          String.join(",", invalid)));
    }

  }
}
