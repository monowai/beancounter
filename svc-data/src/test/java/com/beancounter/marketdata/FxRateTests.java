package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.IsoCurrencyPair;
import com.beancounter.common.utils.RateCalculator;
import com.beancounter.marketdata.providers.fxrates.EcbRates;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FxRateTests {
  private static IsoCurrencyPair USD_USD = IsoCurrencyPair.builder().from("USD").to("USD").build();
  private static IsoCurrencyPair AUD_NZD = IsoCurrencyPair.builder().from("AUD").to("NZD").build();
  private static IsoCurrencyPair NZD_AUD = IsoCurrencyPair.builder().from("NZD").to("AUD").build();
  private static IsoCurrencyPair AUD_USD = IsoCurrencyPair.builder().from("AUD").to("USD").build();
  private static IsoCurrencyPair USD_AUD = IsoCurrencyPair.builder().from("USD").to("AUD").build();

  @Test
  void is_FxRateResponseSerializing() throws Exception {
    File jsonFile = new ClassPathResource("contracts/ecb/ecbEarly.json").getFile();
    EcbRates ecbRates = new ObjectMapper().readValue(jsonFile, EcbRates.class);
    assertThat(ecbRates)
        .isNotNull()
        .hasNoNullFieldsOrProperties()
    ;
    assertThat(ecbRates.getRates()).hasSize(6);
  }

  @Test
  void is_RateCalculatorComputing() {

    Collection<IsoCurrencyPair> pairs =
        getCurrencyPairs(USD_USD, AUD_NZD, NZD_AUD, AUD_USD, USD_AUD);

    Map<String, FxRate> rates = getRateTable();
    String asAt = "2019-11-21";

    FxPairResults values = RateCalculator.compute(asAt, pairs, rates);

    FxRate audUsd = values.getRates().get(AUD_USD);
    FxRate usdAud = values.getRates().get(USD_AUD);
    // Verify that the inverse rate is equal
    BigDecimal calc = BigDecimal.ONE.divide(audUsd.getRate(), 8, RoundingMode.HALF_UP);
    assertThat(usdAud.getRate()).isEqualTo(calc);

  }

  private Collection<IsoCurrencyPair> getCurrencyPairs(IsoCurrencyPair... pairs) {
    Collection<IsoCurrencyPair> results = new ArrayList<>();
    Collections.addAll(results, pairs);
    return results;
  }

  private Map<String, FxRate> getRateTable() {
    Map<String, FxRate> rates = new HashMap<>();
    rates.put("NZD", getRate("NZD", "1.5536294691"));
    rates.put("AUD", getRate("AUD", "1.48261"));
    rates.put("USD", getRate("USD", "1"));
    return rates;
  }

  private FxRate getRate(String to, String rate) {
    return FxRate.builder()
        .rate(new BigDecimal(rate))
        .from(Currency.builder().code("USD").build())
        .to(
            Currency.builder().code(to).build())
        .build();
  }
}
