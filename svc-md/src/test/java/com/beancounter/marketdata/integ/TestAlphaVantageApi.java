package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.AlphaVantageUtils;
import com.beancounter.marketdata.service.MarketDataService;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * .
 *
 * @author mikeh
 * @since 2019-03-04
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class TestAlphaVantageApi {

  private static WireMockRule mockAlphaVantage;

  @Autowired
  MarketDataService marketDataService;

  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockAlphaVantage == null) {
      mockAlphaVantage = new WireMockRule(options().port(8888));
      mockAlphaVantage.start();
    }
  }

  @Test
  void apiErrorMessage() throws Exception {

    File jsonFile = new ClassPathResource("alphavantageError.json").getFile();

    AlphaVantageUtils.mockResponse(mockAlphaVantage, jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("NASDAQ").build()).build();

    MarketData mdResult = marketDataService.getCurrent(asset);
    assertThat(mdResult)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

  @Test
  void apiCallLimitExceeded() throws Exception {

    File jsonFile = new ClassPathResource("alphavantageNote.json").getFile();

    AlphaVantageUtils.mockResponse(mockAlphaVantage, jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("NASDAQ").build()).build();

    MarketData mdResult = marketDataService.getCurrent(asset);
    assertThat(mdResult)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }
}
