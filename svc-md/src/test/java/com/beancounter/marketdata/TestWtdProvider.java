package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Market Data integration with AlphaVantage.co
 *
 * @author mikeh
 * @since 2019-03-03
 */
class TestWtdProvider {

  @Test
  void jsonGoodResponse() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    File jsonFile = new ClassPathResource("wtdMultiAsset.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    assertThat(response)
        .isNotNull()
        .hasFieldOrProperty("date")
        .hasFieldOrProperty("data");
  }

  @Test
  void jsonMessageResponse() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    File jsonFile = new ClassPathResource("wtdMessage.json").getFile();
    WtdResponse response = mapper.readValue(jsonFile, WtdResponse.class);

    assertThat(response)
        .isNotNull()
        .hasFieldOrProperty("message");
  }
}
