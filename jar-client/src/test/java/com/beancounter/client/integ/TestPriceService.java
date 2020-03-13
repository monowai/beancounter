package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.PriceService;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.AssetUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ImportAutoConfiguration(ClientConfig.class)
@SpringBootTest(classes = ClientConfig.class)
public class TestPriceService {

  @Autowired
  private PriceService priceService;

  @Test
  void is_MarketDateOnDateFound() {
    PriceRequest priceRequest = PriceRequest.of(AssetUtils
        .getAsset("EBAY", "NASDAQ"))
        .date("2019-10-18").build();

    PriceResponse response = priceService.getPrices(priceRequest);
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull().hasSize(1);
    MarketData marketData = response.getData().iterator().next();
    assertThat(marketData.getAsset().getMarket()).isNotNull();
    assertThat(marketData)
        .hasFieldOrProperty("close")
        .hasFieldOrProperty("open")
        .hasFieldOrProperty("high")
        .hasFieldOrProperty("low")
        .hasFieldOrProperty("date");
  }

}
