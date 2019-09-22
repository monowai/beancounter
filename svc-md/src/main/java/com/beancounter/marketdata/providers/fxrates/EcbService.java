package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.FxRate;
import com.beancounter.marketdata.service.CurrencyService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EcbService {
  private FxGateway fxGateway;
  private CurrencyService currencyService;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private String currencies;

  @Autowired
  EcbService(FxGateway fxGateway, CurrencyService currencyService) {
    this.fxGateway = fxGateway;
    this.currencyService = currencyService;
    // comma separated list of supported currencies
    currencies = currencyService.delimited(",");
  }

  public Collection<FxRate> getRates(String asAt) {
    EcbRates rates = fxGateway.getRatesForSymbols(
        asAt,
        currencyService.getBase().getCode(),
        currencies);
    Collection<FxRate> results = new ArrayList<>();
    for (String code : rates.getRates().keySet()) {
      results.add(
          FxRate.builder()
              .from(currencyService.getBase())
              .to(currencyService.getCode(code))
              .rate(rates.getRates().get(code))
              .build()
      );
    }
    return results;
  }


  public String date(@NotNull Date inDate) {
    if (inDate == null) {
      throw new BusinessException("Date must not be null");
    }

    return dateFormat.format(inDate);
  }
}
