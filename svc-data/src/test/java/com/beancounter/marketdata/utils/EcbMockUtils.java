package com.beancounter.marketdata.utils;

import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.fxrates.EcbRates;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public class EcbMockUtils {
  private static final DateUtils dateUtils = new DateUtils();

  public static EcbRates get(String date, Map<String, BigDecimal> rates) {
    EcbRates ecbRates = new EcbRates();
    ecbRates.setDate(dateUtils.getDate(date));
    ecbRates.setRates(rates);
    return ecbRates;
  }

  public static Map<String, BigDecimal> getRateMap(
      String eur, String sgd, String gbp, String nzd, String aud) {
    Map<String, BigDecimal> ratesTest = new TreeMap<>();
    ratesTest.put("AUD", new BigDecimal(aud));
    ratesTest.put("EUR", new BigDecimal(eur));
    ratesTest.put("GBP", new BigDecimal(gbp));
    ratesTest.put("NZD", new BigDecimal(nzd));
    ratesTest.put("SGD", new BigDecimal(sgd));
    ratesTest.put("USD", new BigDecimal("1.0"));
    return ratesTest;
  }


}
