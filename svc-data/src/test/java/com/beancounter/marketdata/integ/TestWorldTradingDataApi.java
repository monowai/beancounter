package com.beancounter.marketdata.integ;

import static com.beancounter.common.utils.AssetUtils.getAssetInput;
import static com.beancounter.marketdata.contracts.ContractVerifierBase.AAPL;
import static com.beancounter.marketdata.contracts.ContractVerifierBase.AMP;
import static com.beancounter.marketdata.contracts.ContractVerifierBase.MSFT;
import static com.beancounter.marketdata.contracts.ContractVerifierBase.MSFT_INVALID;
import static com.beancounter.marketdata.utils.WtdMockUtils.getResponseMap;
import static com.beancounter.marketdata.utils.WtdMockUtils.priceDate;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.beancounter.marketdata.utils.WtdMockUtils;
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
 * WorldTradingData API tests.
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

    Collection<AssetInput> assets = new ArrayList<>();
    assets.add(getAssetInput(AMP));

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

    Collection<MarketData> mdResult = wtdService.getMarketData(PriceRequest.builder()
        .date("2019-11-15")
        .assets(assets).build()
    );

    assertThat(mdResult).isNotNull()
        .hasSize(1);

  }

  @Test
  void is_MarketDataDateOverridingRequestDate() throws Exception {

    Collection<AssetInput> inputs = new ArrayList<>();
    inputs.add(getAssetInput(AAPL));
    inputs.add(getAssetInput(MSFT));

    // While the request date is relative to "Today", we are testing that we get back
    //  the date as set in the response from the provider.

    WtdMockUtils.mockWtdResponse(inputs, mockInternet,
        "2019-11-15", // Prices are at T-1. configured date set in -test.yaml
        false,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/AAPL-MSFT.json").getFile());

    Collection<MarketData> mdResult =
        wtdService.getMarketData(PriceRequest.builder()
            .date("2019-11-15")
            .assets(inputs).build());


    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(MSFT)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", "2019-03-08")
            .hasFieldOrPropertyWithValue("asset", MSFT)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("109.16"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("110.51"));
      } else if (marketData.getAsset().equals(AAPL)) {
        assertThat(marketData)
            .hasFieldOrPropertyWithValue("date", "2019-03-08")
            .hasFieldOrPropertyWithValue("asset", AAPL)
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void is_WtdInvalidAssetPriceDefaulting() throws Exception {
    Collection<AssetInput> inputs = new ArrayList<>();

    inputs.add(getAssetInput(AAPL));
    inputs.add(getAssetInput(MSFT_INVALID));

    // Prices are at T-1. configured date set in -test.yaml
    WtdMockUtils.mockWtdResponse(inputs, mockInternet, priceDate, true,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/APPL.json").getFile());

    Collection<MarketData> mdResult = wtdService
        .getMarketData(PriceRequest.builder().assets(inputs).build());

    assertThat(mdResult)
        .isNotNull()
        .hasSize(2);

    // If an invalid asset, then we have a ZERO price
    for (MarketData marketData : mdResult) {
      if (marketData.getAsset().equals(MSFT)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
      } else if (marketData.getAsset().equals(AAPL)) {
        assertThat(marketData)
            .hasFieldOrProperty("date")
            .hasFieldOrPropertyWithValue("open", new BigDecimal("170.32"))
            .hasFieldOrPropertyWithValue("close", new BigDecimal("172.91"));
      }

    }
  }

  @Test
  void is_NoDataReturned() throws Exception {

    Collection<AssetInput> inputs = new ArrayList<>();
    inputs.add(getAssetInput(MSFT));

    WtdMockUtils.mockWtdResponse(inputs, mockInternet, "2019-11-15", true,
        new ClassPathResource(WtdMockUtils.WTD_PATH + "/NoData.json").getFile());
    Collection<MarketData> prices = wtdService.getMarketData(
        PriceRequest.builder()
            .date("2019-11-15")
            .assets(inputs).build());

    assertThat(prices).hasSize(inputs.size());
    assertThat(
        prices.iterator().next()).hasFieldOrPropertyWithValue("close", BigDecimal.ZERO);
  }

}
