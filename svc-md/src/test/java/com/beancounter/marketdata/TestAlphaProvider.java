package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.alpha.AlphaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestAlphaProvider {

  @Test
  void jsonSerialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    File jsonFile = new ClassPathResource("alphavantage.json").getFile();
    MarketData response = mapper.readValue(jsonFile, AlphaResponse.class);

    assertThat(response)
        .isNotNull()
        .hasFieldOrProperty("asset")
        .hasFieldOrProperty("date")
        .hasFieldOrPropertyWithValue("open", new BigDecimal("112.0400"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("112.8800"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("111.7300"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("112.0300"));


  }
}
