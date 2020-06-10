package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
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
public class TestAssetIngestService {

  @Autowired
  private AssetIngestService assetIngestService;

  @Test
  void is_HydratedAssetFound() {
    Asset asset = assetIngestService.resolveAsset("NASDAQ", "MSFT", "Microsoft"
    );

    assertThat(asset).isNotNull();
    assertThat(asset.getId()).isNotNull();
    assertThat(asset.getMarket()).isNotNull();
    assertThat(asset.getMarket().getCurrency()).isNotNull();
  }

  @Test
  void is_MockAssetFound() {
    Asset asset = assetIngestService
        .resolveAsset("MOCK", "MSFT", "Microsoftie"
        );
    assertThat(asset).isNotNull();
    assertThat(asset).isNotNull().hasFieldOrPropertyWithValue("name", "Microsoftie");
  }

  @Test
  void is_NotFound() {
    assertThrows(BusinessException.class, () ->
        assetIngestService
            .resolveAsset("NASDAQ", "ABC", "Microsoft"
            ));
  }
}
