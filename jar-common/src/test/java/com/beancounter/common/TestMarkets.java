package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class TestMarkets {
  @Test
  @VisibleForTesting
  void is_MarketResponseSerializing() throws Exception {
    Market nasdaq = Market.builder().code("NASDAQ")
        .currency(Currency.builder().code("USD").build())
        .build();
    Market nzx = Market.builder().code("NZX")
        .currency(Currency.builder().code("NZD").build())
        .build();

    Collection<Market> markets = new ArrayList<>();
    markets.add(nasdaq);
    markets.add(nzx);
    MarketResponse marketResponse = MarketResponse.builder().data(markets).build();
    assertThat(marketResponse.getData()).hasSize(2);

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(marketResponse);
    MarketResponse fromJson = objectMapper.readValue(json, MarketResponse.class);
    assertThat(fromJson).isEqualToComparingFieldByField(marketResponse);


  }
}
