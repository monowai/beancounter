package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.DataProviderUtils.getResponseMap;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.DataProviderUtils;
import com.beancounter.marketdata.providers.wtd.WtdProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
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
  void asxMarketInputConvertsToAxMarket() throws Exception {
    Asset amp = Asset.builder().code("AMP")
        .market(Market.builder().code("ASX").build())
        .build();

    File jsonFile = new ClassPathResource("/wtdAsxResponse.json").getFile();
    HashMap<String, Object> response = getResponseMap(jsonFile);
    Collection<Asset> assets = new ArrayList<>();
    assets.add(amp);

    mockInternet
        .stubFor(
            get(urlEqualTo(
                "/api/v1/history_multi_single_day?symbol=AMP.AX&date="
                    + wtdProviderService.getDate()
                    + "&api_token=demo"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(new ObjectMapper().writeValueAsString(response))
                    .withStatus(200)));

    Collection<MarketData> mdResult = wtdProviderService.getCurrent(assets);

    assertThat(mdResult).isNotNull()
        .hasSize(1);

  }

  @Test
  void marketDataReturnsPricesWithMarketDate_WhenRequestDateIsLater() throws Exception {

    Asset aapl =
        Asset.builder().code("AAPL").market(DataProviderUtils.nasdaq()).build();
    Asset msft =
        Asset.builder().code("MSFT").market(DataProviderUtils.nasdaq()).build();

    Collection<Asset> assets = new ArrayList<>();
    assets.add(aapl);
    assets.add(msft);

    File jsonFile = new ClassPathResource("/wtdMultiAsset.json").getFile();
    // While the request date is relative to "Today", we are testing that we get back
    //  the date as set in the response from the provider.

    DataProviderUtils.mockWtdResponse(assets, mockInternet,
        wtdProviderService.getDate(),
        false,
        jsonFile);

    Collection<MarketData> mdResult = wtdProviderService.getCurrent(assets);
    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    // Compare with the date in the mocked response
    ZonedDateTime compareTo = ZonedDateTime.of(
        LocalDate.parse("2019-03-08").atStartOfDay(), ZoneId.of("UTC"));

    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(msft)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", Date.from(compareTo.toInstant()))
            .hasFieldOrPropertyWithValue("asset", msft)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("109.16"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("110.51"));
      } else if (marketData.getAsset().equals(aapl)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", Date.from(compareTo.toInstant()))
            .hasFieldOrPropertyWithValue("asset", aapl)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void apiGetMarketDataWithAnInvalidAsset() throws Exception {

    Asset aapl =
        Asset.builder().code("AAPL").market(Market.builder().code("NASDAQ").build()).build();

    Asset msft =
        Asset.builder().code("MSFTx").market(Market.builder().code("NASDAQ").build()).build();

    Collection<Asset> assets = new ArrayList<>();

    assets.add(aapl);
    assets.add(msft);

    File jsonFile = new ClassPathResource("wtdWithInvalidAsset.json").getFile();
    DataProviderUtils.mockWtdResponse(mockInternet, assets, wtdProviderService.getDate(), jsonFile);


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

    DataProviderUtils.mockWtdResponse(mockInternet, assets, wtdProviderService.getDate(), jsonFile);

    assertThrows(BusinessException.class, () -> wtdProviderService.getCurrent(assets));
  }

}
