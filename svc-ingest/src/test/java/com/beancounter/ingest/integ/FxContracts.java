package com.beancounter.ingest.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.ingest.service.FxRateService;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Tag("slow")
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@SpringBootTest
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = "beancounter:svc-md:+:stubs:8090")
@ActiveProfiles("test")
class FxContracts {

  @Autowired
  private FxRateService fxRateService;

  @Test
  @VisibleForTesting
  void is_FxContractHonoured() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("EUR").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("GBP").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("NZD").build());

    String testDate = "2019-07-28";
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
  @VisibleForTesting
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
