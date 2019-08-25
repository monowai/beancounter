package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.DataProviderUtils;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
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
@Tag("slow")
class TestAlphaVantageApi {

  private static WireMockRule mockInternet;

  @Autowired
  private MdFactory mdFactory;

  @Autowired
  private void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(9999));
      mockInternet.start();
    }
  }

  @Test
  void apiErrorMessage() throws Exception {

    File jsonFile = new ClassPathResource("alphavantageError.json").getFile();

    DataProviderUtils.mockAlphaResponse(mockInternet, jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("ASX").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    MarketData mdResult = alphaProvider.getCurrent(asset);
    assertThat(mdResult)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

  @Test
  void apiInvalidKey() throws Exception {

    File jsonFile = new ClassPathResource("alphavantageInfo.json").getFile();

    DataProviderUtils.mockAlphaResponse(mockInternet, jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("ASX").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    MarketData mdResult = alphaProvider.getCurrent(asset);
    assertThat(mdResult)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

  @Test
  void apiCallLimitExceeded() throws Exception {

    File jsonFile = new ClassPathResource("alphavantageNote.json").getFile();

    DataProviderUtils.mockAlphaResponse(mockInternet, jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("ASX").build()).build();

    MarketData mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getCurrent(asset);

    assertThat(mdResult)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }
}
