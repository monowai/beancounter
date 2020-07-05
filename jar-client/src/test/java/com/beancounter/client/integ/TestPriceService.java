package com.beancounter.client.integ;

import static com.beancounter.common.utils.AssetUtils.getAssetInput;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.services.PriceService;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.util.ArrayList;
import java.util.Collection;
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

  @Autowired
  private AssetIngestService assetIngestService;

  @Test
  void is_MarketDataFoundOnDate() {
    Asset asset = assetIngestService
        .resolveAsset("NASDAQ", "EBAY", "EBAY");

    Collection<AssetInput> assets = new ArrayList<>();
    assets.add(getAssetInput(asset));

    PriceRequest priceRequest = new PriceRequest("2019-10-18", assets);

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
        .hasFieldOrProperty("priceDate");
  }

}
