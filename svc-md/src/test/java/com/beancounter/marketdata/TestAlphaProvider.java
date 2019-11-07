package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.alpha.AlphaService;
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
  private ObjectMapper mapper = new AlphaService().getAlphaObjectMapper();

  @Test
  void is_NullAsset() throws Exception {
    ObjectMapper mapper = new AlphaService().getAlphaObjectMapper();
    File jsonFile = new ClassPathResource("alphavantage-empty-response.json").getFile();
    assertThat(mapper.readValue(jsonFile, MarketData.class)).isNull();
  }

  @Test
  void is_ResponseWithMarketCodeSerialized() throws Exception {
    File jsonFile = new ClassPathResource("alphavantage-asx.json").getFile();
    MarketData marketData = validateResponse(jsonFile);
    assertThat(
        marketData.getAsset())
        .hasFieldOrPropertyWithValue("code", "MSFT")
        .hasFieldOrPropertyWithValue("market.code", "NASDAQ");

  }

  @Test
  void is_ResponseWithoutMarketCodeSetToUs() throws Exception {
    File jsonFile = new ClassPathResource("alphavantage-nasdaq.json").getFile();
    MarketData marketData = validateResponse(jsonFile);
    assertThat(
        marketData.getAsset())
        .hasFieldOrPropertyWithValue("code", "MSFT")
        .hasFieldOrPropertyWithValue("market.code", "US");
  }

  private MarketData validateResponse(File jsonFile) throws Exception {
    MarketData marketData = mapper.readValue(jsonFile, MarketData.class);

    assertThat(marketData)
        .isNotNull()
        .hasFieldOrProperty("asset")
        .hasFieldOrProperty("date")
        .hasFieldOrPropertyWithValue("open", new BigDecimal("112.0400"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("112.8800"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("111.7300"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("112.0300"));
    return marketData;
  }
}
