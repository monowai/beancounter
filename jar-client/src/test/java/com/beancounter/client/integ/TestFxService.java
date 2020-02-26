package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.FxRateService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.CurrencyPair;
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
public class TestFxService {

  @Autowired
  private FxRateService fxRateService;

  @Test
  void is_FxContractHonoured() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("EUR").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("GBP").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("NZD").build());

    String testDate = "2019-11-12";
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(currencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    assertThat(fxPairResults.getRates().size()).isEqualTo(currencyPairs.size());

    for (CurrencyPair currencyPair : currencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(currencyPair);
      assertThat(fxPairResults.getRates().get(currencyPair))
          .hasFieldOrPropertyWithValue("date", testDate);
    }
  }

  @Test
  void is_EarlyDateWorking() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("SGD").build());
    currencyPairs.add(CurrencyPair.builder().from("GBP").to("NZD").build());

    String testDate = "1996-07-27"; // Earlier than when ECB started recording rates
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(currencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    for (CurrencyPair currencyPair : currencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(currencyPair);
      assertThat(fxPairResults.getRates().get(currencyPair))
          .hasFieldOrPropertyWithValue("date", "1999-01-04");
    }
  }
}
