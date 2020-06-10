package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.model.FxRate;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EcbService {
  private final FxGateway fxGateway;
  private final CurrencyService currencyService;
  private final String currencies;
  private final EcbDate ecbDate = new EcbDate();
  private DateUtils dateUtils = new DateUtils();


  @Autowired
  EcbService(FxGateway fxGateway, CurrencyService currencyService) {
    this.fxGateway = fxGateway;
    this.currencyService = currencyService;
    // comma separated list of supported currencies
    currencies = currencyService.delimited(",");
  }

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  public Collection<FxRate> getRates(String asAt) {
    EcbRates rates = fxGateway.getRatesForSymbols(
        ecbDate.getValidDate(asAt),
        currencyService.getBase().getCode(),
        currencies);

    Collection<FxRate> results = new ArrayList<>();
    for (String code : rates.getRates().keySet()) {
      results.add(
          FxRate.builder()
              .from(currencyService.getBase())
              .to(currencyService.getCode(code))
              .rate(rates.getRates().get(code))
              .date(dateUtils.getDateString(rates.getDate()))
              .build()
      );
    }
    return results;
  }


}
