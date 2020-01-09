package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.WtdMockUtils.getResponseMap;
import static com.beancounter.marketdata.WtdMockUtils.priceDate;
import static com.beancounter.marketdata.integ.ContractVerifierBase.aapl;
import static com.beancounter.marketdata.integ.ContractVerifierBase.amp;
import static com.beancounter.marketdata.integ.ContractVerifierBase.msft;
import static com.beancounter.marketdata.integ.ContractVerifierBase.msftInvalid;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.WtdMockUtils;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
  private WtdService wtdService;


  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(8888));
      mockInternet.start();
    }
  }

  @Test
  void is_AsxMarketConvertedToAx() throws Exception {

    HashMap<String, Object> response = getResponseMap(
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/AMP-ASX.json").getFile());

    Collection<Asset> assets = new ArrayList<>();
    assets.add(amp);

    mockInternet
        .stubFor(
            get(urlEqualTo(
                "/api/v1/history_multi_single_day?symbol=AMP.AX&date="
                    + "2019-11-15"
                    + "&api_token=demo"))
                .willReturn(aResponse()
                    .withHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(new ObjectMapper().writeValueAsString(response))
                    .withStatus(200)));

    Collection<MarketData> mdResult = wtdService.getPrices(PriceRequest.builder()
        .date("2019-11-15")
        .assets(assets).build()
    );

    assertThat(mdResult).isNotNull()
        .hasSize(1);

  }

  @Test
  void is_MarketDataDateOverridingRequestDate() throws Exception {

    Collection<Asset> assets = new ArrayList<>();
    assets.add(aapl);
    assets.add(msft);

    // While the request date is relative to "Today", we are testing that we get back
    //  the date as set in the response from the provider.

    WtdMockUtils.mockWtdResponse(assets, mockInternet,
        "2019-11-15", // Prices are at T-1. configured date set in -test.yaml
        false,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/AAPL-MSFT.json").getFile());

    Collection<MarketData> mdResult =
        wtdService.getPrices(PriceRequest.builder()
            .date("2019-11-15")
            .assets(assets).build());


    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(msft)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", "2019-03-08")
            .hasFieldOrPropertyWithValue("asset", msft)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("109.16"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("110.51"));
      } else if (marketData.getAsset().equals(aapl)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", "2019-03-08")
            .hasFieldOrPropertyWithValue("asset", aapl)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void is_WtdInvalidAssetPriceDefaulting() throws Exception {
    Collection<Asset> assets = new ArrayList<>();

    assets.add(aapl);
    assets.add(msftInvalid);

    // Prices are at T-1. configured date set in -test.yaml
    WtdMockUtils.mockWtdResponse(assets, mockInternet, priceDate, true,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/APPL.json").getFile());

    Collection<MarketData> mdResult = wtdService.getPrices(
        PriceRequest.builder().assets(assets).build());

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
  void is_NoDataReturned() throws Exception {

    Collection<Asset> assets = new ArrayList<>();
    assets.add(msft);

    WtdMockUtils.mockWtdResponse(assets, mockInternet, "2019-11-15", true,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/NoData.json").getFile());
    Collection<MarketData> prices = wtdService.getPrices(
        PriceRequest.builder()
            .date("2019-11-15")
            .assets(assets).build());

    assertThat(prices).hasSize(assets.size());
    assertThat(
        prices.iterator().next()).hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
    // Changed assumption from exception to no data
    //    assertThrows(BusinessException.class, () -> wtdService.getPrices(
    //        PriceRequest.builder()
    //            .date("2019-11-15")
    //            .assets(assets).build()));
  }

}
