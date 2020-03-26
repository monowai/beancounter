package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.services.ClientConfig;
import com.beancounter.client.services.StaticService;
import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
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
public class TestStaticService {
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
        staticService.getMarket(null));
    assertThrows(BusinessException.class, () ->
        staticService.getMarket("ERR"));
  }

  @Test
  void are_CurrenciesFound() {
    CurrencyResponse currencies = staticService.getCurrencies();
    assertThat(currencies).isNotNull();
    assertThat(currencies.getData()).isNotEmpty();
  }

  @Test
  void is_CurrencyFound() {
    Currency currency = staticService.getCurrency("USD");
    assertThat(currency).isNotNull();
    assertThat(currency).hasNoNullFieldsOrProperties();
  }


}
