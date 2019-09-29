package com.beancounter.ingest.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import com.beancounter.ingest.service.FxRateService;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Tag("slow")
@ImportAutoConfiguration( {FeignAutoConfiguration.class})
@SpringBootTest
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = "beancounter:svc-md:+:stubs:8090")
@DirtiesContext
@ActiveProfiles("test")
class FxContracts {

  @Autowired
  private FxRateService fxRateService;

  @Test
  void given_FxRatesRequest_ThenResultsAreReturned() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("EUR").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("GBP").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("NZD").build());

    String testDate = "2019-07-27";
    FxResults fxResults = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(currencyPairs)
        .build());
    assertThat(fxResults).isNotNull().hasNoNullFieldsOrProperties();
    assertThat(fxResults.getData().keySet()).hasSize(1);
    assertThat(fxResults.getData()).containsKeys(testDate);
    FxPairResults fxPairResults = fxResults.getData().get(testDate);
    for (CurrencyPair currencyPair : currencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(currencyPair);
      assertThat(fxPairResults.getRates().get(currencyPair))
          .hasFieldOrPropertyWithValue("date", testDate);
    }
  }
}
