package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.marketdata.DataProviderUtils;
import com.beancounter.marketdata.service.FxService;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
class FxServiceTests {

  private static WireMockRule mockInternet;
  private FxService fxService;

  @Autowired
  private void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(7777));
      mockInternet.start();
    }
  }

  @Autowired
  void setFxService(FxService fxService) {
    this.fxService = fxService;
  }

  @Test
  void getRates() throws Exception {
    File jsonFile = new ClassPathResource("ecb-fx-rates.json").getFile();
    DataProviderUtils.mockGetResponse(
        mockInternet,
        "/2019-08-27?base=USD&symbols=AUD%2CEUR%2CGBP%2CUSD%2CNZD",
        jsonFile);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    Date date = formatter.parse("2019-08-27");
    CurrencyPair nzdUsd = CurrencyPair.builder().from("NZD").to("USD").build();
    CurrencyPair usdNzd = CurrencyPair.builder().from("USD").to("NZD").build();
    CurrencyPair usdUsd = CurrencyPair.builder().from("USD").to("USD").build();
    CurrencyPair nzdNzd = CurrencyPair.builder().from("NZD").to("NZD").build();
    Collection<CurrencyPair> pairs = new ArrayList<>();
    pairs.add(nzdUsd);
    pairs.add(usdNzd);
    pairs.add(nzdNzd);
    pairs.add(usdUsd);
    Map<CurrencyPair, FxRate> results = fxService.getRates(date, pairs);
    assertThat(results)
        .containsKeys(nzdUsd, usdNzd)
        .hasSize(pairs.size());

    for (CurrencyPair currencyPair : results.keySet()) {
      assertThat(results.get(currencyPair).getDate()).isNotNull();
    }

  }

}
