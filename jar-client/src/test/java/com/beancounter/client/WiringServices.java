package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.services.AssetService;
import com.beancounter.client.services.ClientConfig;
import com.beancounter.client.services.ExchangeService;
import com.beancounter.client.services.FxRateService;
import com.beancounter.client.services.PortfolioService;
import com.beancounter.client.services.PriceService;
import com.beancounter.client.services.StaticService;
import com.beancounter.client.services.TrnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;

@ImportAutoConfiguration(ClientConfig.class)
@SpringBootTest(classes = ClientConfig.class)
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
public class WiringServices {
  @Autowired
  private AssetService assetService;
  @Autowired
  private ExchangeService exchangeService;
  @Autowired
  private FxRateService fxRateService;
  @Autowired
  private PortfolioService portfolioService;
  @Autowired
  private PriceService priceService;
  @Autowired
  private StaticService staticService;
  @Autowired
  private TrnService trnService;

  @Test
  void is_Wired() {
    assertThat(assetService).isNotNull();
    assertThat(exchangeService).isNotNull();
    assertThat(fxRateService).isNotNull();
    assertThat(portfolioService).isNotNull();
    assertThat(priceService).isNotNull();
    assertThat(staticService).isNotNull();
    assertThat(trnService).isNotNull();
  }
}
