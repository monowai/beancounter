package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.request.FxRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestFx {
  @Test
  void rateRequestSerialization() throws Exception {
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
}
