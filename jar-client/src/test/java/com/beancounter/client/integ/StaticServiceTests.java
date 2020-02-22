package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.StaticService;
import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
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
public class StaticServiceTests {
  @Autowired
  private StaticService staticService;

  @Test
  void are_MarketsFound() {
    MarketResponse markets = staticService.getMarkets();
    assertThat(markets).isNotNull();
    assertThat(markets.getData()).isNotEmpty();
  }

  @Test
  void is_MarketIllegalArgumentsThrowing() {
    assertThrows(BusinessException.class, () ->
        staticService.resolveMarket(null));
    assertThrows(BusinessException.class, () ->
        staticService.resolveMarket("ERR"));
  }

  @Test
  void are_CurrenciesFound() {
    CurrencyResponse currencies = staticService.getCurrencies();
    assertThat(currencies).isNotNull();
    assertThat(currencies.getData()).isNotEmpty();
  }



}
