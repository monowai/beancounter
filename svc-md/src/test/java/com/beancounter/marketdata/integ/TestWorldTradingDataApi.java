package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.DataProviderUtils;
import com.beancounter.marketdata.providers.wtd.WtdProviderService;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
//    (classes = {WtdProviderService.class,
//    WtdRequestor.class,
//    WtdRequest.class})
//@ImportAutoConfiguration({FeignAutoConfiguration.class})
@ActiveProfiles("test")
class TestWorldTradingDataApi {

  private static WireMockRule mockInternet;

  @Autowired
  WtdProviderService wtdProviderService;

  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(8888));
      mockInternet.start();
    }
  }

  @Test
  void apiGetMarketData() throws Exception {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();
    Asset msft =
        Asset.builder().code("MSFT").market(Market.builder().code("NASDAQ").build()).build();

    Collection<Asset> assets = new ArrayList<>();
    assets.add(aapl);
    assets.add(msft);

    File jsonFile = new ClassPathResource("wtdMultiAsset.json").getFile();
    DataProviderUtils.mockWtdResponse(mockInternet, assets, jsonFile);

    Collection<MarketData> mdResult = wtdProviderService.getCurrent(assets);
    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(msft)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("asset", msft)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("109.16"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("110.51"));
      } else if (marketData.getAsset().equals(aapl)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("asset", aapl)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void apiGetMarketDataWithInvalidAsset() throws Exception {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();

    Asset msft =
        Asset.builder().code("MSFTx").market(Market.builder().code("NASDAQ").build()).build();

    Collection<Asset> assets = new ArrayList<>();

    assets.add(aapl);
    assets.add(msft);

    File jsonFile = new ClassPathResource("wtdWithInvalidAsset.json").getFile();
    DataProviderUtils.mockWtdResponse(mockInternet, assets, jsonFile);


    Collection<MarketData> mdResult = wtdProviderService.getCurrent(assets);
    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    // If an invalid asset, then we have a ZERO price
    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(msft)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
      } else if (marketData.getAsset().equals(aapl)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void apiMessage() throws Exception {

    File jsonFile = new ClassPathResource("wtdMessage.json").getFile();
    Asset msft =
        Asset.builder().code("MSFT").market(Market.builder().code("NASDAQ").build()).build();

    Collection<Asset> assets = new ArrayList<>();
    assets.add(msft);
    DataProviderUtils.mockWtdResponse(mockInternet, assets, jsonFile);

    assertThrows(BusinessException.class, () -> wtdProviderService.getCurrent(assets));
  }

}
