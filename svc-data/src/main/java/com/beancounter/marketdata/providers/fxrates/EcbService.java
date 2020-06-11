package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EcbService {
  private final FxGateway fxGateway;
  private final CurrencyService currencyService;

  private final EcbDate ecbDate = new EcbDate();
  private DateUtils dateUtils = new DateUtils();


  @Autowired
  EcbService(FxGateway fxGateway, CurrencyService currencyService) {
    this.fxGateway = fxGateway;
    this.currencyService = currencyService;
  }

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  public Collection<FxRate> getRates(String asAt) {
    EcbRates rates = fxGateway.getRatesForSymbols(
        ecbDate.getValidDate(asAt),
        currencyService.getBaseCurrency().getCode(),
        getCurrencies());

    Collection<FxRate> results = new ArrayList<>();
    for (String code : rates.getRates().keySet()) {
      results.add(
          FxRate.builder()
              .from(currencyService.getBaseCurrency())
              .to(currencyService.getCode(code))
              .rate(rates.getRates().get(code))
              .date(dateUtils.getDateString(rates.getDate()))
              .build()
      );
    }
    return results;
  }

  private String getCurrencies() {
    Iterable<Currency> values = currencyService.getCurrencies();
    StringBuilder result = null;
    for (Currency value : values) {
      if (result == null) {
        result = Optional.ofNullable(value.getCode()).map(StringBuilder::new).orElse(null);
      } else {
        result.append(",").append(value.getCode());
      }
    }
    return result == null ? null : result.toString();

  }


}
