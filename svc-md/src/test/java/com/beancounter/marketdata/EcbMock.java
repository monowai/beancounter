package com.beancounter.marketdata;

import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.fxrates.EcbRates;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class EcbMock {

  public static EcbRates get(String date, Map<String, BigDecimal> rates) {
    EcbRates ecbRates = new EcbRates();
    ecbRates.setDate(DateUtils.getDate(date));
    ecbRates.setRates(rates);
    return ecbRates;
  }

  public static Map<String, BigDecimal> getRateMap(
      String eur, String sgd, String gbp, String nzd, String aud) {
    Map<String, BigDecimal> ratesTest = new HashMap<>();
    ratesTest.put("EUR", new BigDecimal(eur));
    ratesTest.put("SGD", new BigDecimal(sgd));
    ratesTest.put("USD", new BigDecimal("1.0"));
    ratesTest.put("GBP", new BigDecimal(gbp));
    ratesTest.put("NZD", new BigDecimal(nzd));
    ratesTest.put("AUD", new BigDecimal(aud));
    return ratesTest;
  }


}
