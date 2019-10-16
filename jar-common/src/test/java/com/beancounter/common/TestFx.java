package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.utils.RateCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestFx {

  @Test
  @VisibleForTesting
  void is_RateRequestSerializing() throws Exception {
    CurrencyPair pair = CurrencyPair.builder().from("THIS").to("THAT").build();
    Collection<CurrencyPair> pairs = new ArrayList<>();
    pairs.add(pair);
    FxRequest fxRequest = FxRequest.builder().pairs(pairs).build();
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(fxRequest);
    FxRequest fromJson = mapper.readValue(json, FxRequest.class);
    assertThat(fromJson)
        .isEqualToComparingFieldByFieldRecursively(fxRequest);

  }

  @Test
  @VisibleForTesting
  void is_FxRequestPairsIgnoringDuplicates() {
    CurrencyPair pair = CurrencyPair.builder().from("THIS").to("THAT").build();
    FxRequest fxRequest = FxRequest.builder().build();
    fxRequest.add(pair);
    fxRequest.add(pair);
    assertThat(fxRequest.getPairs()).hasSize(1);
  }

  @Test
  @VisibleForTesting
  void is_FxResultSerializing() throws Exception {
    CurrencyPair nzdUsd = CurrencyPair.builder().from("NZD").to("USD").build();
    CurrencyPair usdUsd = CurrencyPair.builder().from("USD").to("USD").build();

    Collection<CurrencyPair> pairs = new ArrayList<>();
    pairs.add(nzdUsd);
    pairs.add(usdUsd);
    Map<String, FxRate> rateMap = new HashMap<>();
    rateMap.put("NZD", FxRate.builder()
        .to(Currency.builder().code("NZD").build())
        .from(Currency.builder().code("USD").build())
        .rate(BigDecimal.TEN).build());

    rateMap.put("USD", FxRate.builder()
        .to(Currency.builder().code("USD").build())
        .from(Currency.builder().code("USD").build())
        .rate(BigDecimal.ONE).build());

    Map<String, FxPairResults> rateResults = new RateCalculator()
        .compute("2019/08/27", pairs, rateMap);

    ObjectMapper objectMapper = new ObjectMapper();
    FxResponse fxResponse = FxResponse.builder().data(rateResults).build();
    String json = objectMapper.writeValueAsString(fxResponse);
    FxResponse fromJson = objectMapper.readValue(json, FxResponse.class);
    assertThat(fromJson.getData()).isNotNull();
    assertThat(fromJson)
        .isNotNull()
        .isEqualToComparingFieldByField(fxResponse);

  }
}
