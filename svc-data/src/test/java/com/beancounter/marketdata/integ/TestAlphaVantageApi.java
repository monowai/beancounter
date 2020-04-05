package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.utils.AlphaMockUtils.alphaContracts;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import com.beancounter.marketdata.utils.AlphaMockUtils;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import java.util.Collection;
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

  private static WireMockRule alphaVantage;

  @Autowired
  private MdFactory mdFactory;

  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (alphaVantage == null) {
      alphaVantage = new WireMockRule(options().port(9999));
      alphaVantage.start();
    }
  }

  @Test
  void is_ApiErrorMessageHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageError.json").getFile();

    AlphaMockUtils.mockAlphaResponse(alphaVantage, "API.ERR", jsonFile);
    Asset asset =
        Asset.builder().code("API").market(Market.builder().code("ERR").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    Collection<MarketData> results = alphaProvider.getMarketData(PriceRequest.of(asset).build());
    assertThat(results)
        .isNotNull()
        .hasSize(1);

    assertThat(results.iterator().next())
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

  @Test
  void is_ApiInvalidKeyHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageInfo.json").getFile();

    AlphaMockUtils.mockAlphaResponse(alphaVantage, "API.KEY", jsonFile);
    Asset asset =
        Asset.builder().code("API")
            .market(Market.builder().code("KEY").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    Collection<MarketData> results = alphaProvider.getMarketData(PriceRequest.of(asset).build());
    assertThat(results)
        .isNotNull()
        .hasSize(1);

    assertThat(results.iterator().next())
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);

  }

  @Test
  void is_ApiCallLimitExceededHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageNote.json").getFile();

    AlphaMockUtils.mockAlphaResponse(alphaVantage, "API.X", jsonFile);
    Asset asset =
        Asset.builder().code("API").market(Market.builder().code("X").build()).build();

    Collection<MarketData> results = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(PriceRequest.of(asset).build());

    assertThat(results)
        .isNotNull()
        .hasSize(1);

    assertThat(results.iterator().next())
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

  @Test
  void is_SuccessHandledForAsx() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantage-asx.json").getFile();

    AlphaMockUtils.mockAlphaResponse(alphaVantage, "ABC.AX", jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("ASX").build()).build();

    Collection<MarketData> mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(PriceRequest.of(asset).build());

    MarketData marketData = mdResult.iterator().next();
    assertThat(marketData)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", new BigDecimal("112.0300"))
        .hasFieldOrPropertyWithValue("open", new BigDecimal("112.0400"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("111.7300"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("112.8800"))
    ;

  }
}
