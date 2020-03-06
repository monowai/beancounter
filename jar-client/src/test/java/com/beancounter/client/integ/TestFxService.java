package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.ClientConfig;
import com.beancounter.client.FxRateService;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.IsoCurrencyPair;
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
    Collection<IsoCurrencyPair> isoCurrencyPairs = new ArrayList<>();
    isoCurrencyPairs.add(IsoCurrencyPair.builder().from("USD").to("EUR").build());
    isoCurrencyPairs.add(IsoCurrencyPair.builder().from("USD").to("GBP").build());
    isoCurrencyPairs.add(IsoCurrencyPair.builder().from("USD").to("NZD").build());

    String testDate = "2019-11-12";
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(isoCurrencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    assertThat(fxPairResults.getRates().size()).isEqualTo(isoCurrencyPairs.size());

    for (IsoCurrencyPair isoCurrencyPair : isoCurrencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(isoCurrencyPair);
      assertThat(fxPairResults.getRates().get(isoCurrencyPair))
          .hasFieldOrPropertyWithValue("date", testDate);
    }
  }

  @Test
  void is_EarlyDateWorking() {
    Collection<IsoCurrencyPair> isoCurrencyPairs = new ArrayList<>();
    isoCurrencyPairs.add(IsoCurrencyPair.builder().from("USD").to("SGD").build());
    isoCurrencyPairs.add(IsoCurrencyPair.builder().from("GBP").to("NZD").build());

    String testDate = "1996-07-27"; // Earlier than when ECB started recording rates
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(isoCurrencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    for (IsoCurrencyPair isoCurrencyPair : isoCurrencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(isoCurrencyPair);
      assertThat(fxPairResults.getRates().get(isoCurrencyPair))
          .hasFieldOrPropertyWithValue("date", "1999-01-04");
    }
  }

  @Test
  void is_EmptyResponseReturning() {
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder().build());
    assertThat(fxResponse).isNotNull();
    assertThat(fxResponse.getData().getRates()).isEmpty();
  }
}
