package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TestMarkets {

  static final Currency USD = new Currency("USD");
  static final Currency NZD = new Currency("NZD");
  ObjectMapper jsonMapper = new ObjectMapper().registerModule(new KotlinModule());
  ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new KotlinModule());

  @Test
  void is_MarketResponseSerializing() throws Exception {
    Market nasdaq = new Market("NASDAQ", USD);
    Market nzx = new Market("NZX", NZD);

    Collection<Market> markets = new ArrayList<>();
    markets.add(nasdaq);
    markets.add(nzx);
    MarketResponse marketResponse = new MarketResponse(markets);
    assertThat(marketResponse.getData()).hasSize(2);

    String json = jsonMapper.writeValueAsString(marketResponse);
    MarketResponse fromJson = jsonMapper.readValue(json, MarketResponse.class);
    assertThat(fromJson.getData()).containsExactly(nasdaq, nzx);
  }

  @Test
  void fromJson() throws Exception {
    File file = new ClassPathResource("application-markets.yml").getFile();
    MarketResponse marketResponse = yamlMapper.readValue(file, MarketResponse.class);
    assertThat(marketResponse).isNotNull();
    assertThat(marketResponse.getData()).hasSize(2);
  }
}
