package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxRate;
import com.beancounter.marketdata.DataProviderUtils;
import com.beancounter.marketdata.providers.fxrates.EcbRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Tag("slow")
class FxMvcTests {

  private static WireMockRule mockInternet;

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  @VisibleForTesting
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(

    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(7777));
      mockInternet.start();
    }
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

  }

  @Test
  @VisibleForTesting
  void is_FxRateResultsObjectReturned() throws Exception {
    File rateResponse = new ClassPathResource("ecb-fx-rates.json").getFile();
    DataProviderUtils.mockGetResponse(
        mockInternet,
        // Matches all supported currencies
        "/2019-08-27?base=USD&symbols=AUD,SGD,EUR,GBP,USD,NZD",
        rateResponse);
    String date = "2019-08-27";
    CurrencyPair nzdUsd = CurrencyPair.builder().from("NZD").to("USD").build();
    CurrencyPair usdNzd = CurrencyPair.builder().from("USD").to("NZD").build();
    CurrencyPair usdUsd = CurrencyPair.builder().from("USD").to("USD").build();
    CurrencyPair nzdNzd = CurrencyPair.builder().from("NZD").to("NZD").build();

    ObjectMapper objectMapper = new ObjectMapper();

    FxRequest fxRequest = FxRequest.builder().rateDate(date).build();
    fxRequest.add(nzdUsd).add(usdNzd).add(usdUsd).add(nzdNzd);
    MvcResult mvcResult = mockMvc.perform(
        post("/fx")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(fxRequest)
            )
    ).andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andReturn();

    FxResponse fxResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), FxResponse.class);

    FxPairResults results = fxResponse.getData().get(date);
    assertThat(results.getRates()).isNotNull().hasSize(fxRequest.getPairs().size());
    Map<CurrencyPair, FxRate> theRates = results.getRates();
    assertThat(theRates)
        .containsKeys(nzdUsd, usdNzd);

    for (CurrencyPair currencyPair : theRates.keySet()) {
      assertThat(results.getRates().get(currencyPair).getDate()).isNotNull();
    }

  }

  @Test
  @VisibleForTesting
  void is_EarliestRateDateValid() {
    EcbRules ecbRules = new EcbRules();
    assertThat(ecbRules.getValidDate("1990-01-01"))
        .isEqualTo(EcbRules.earliest);
  }

}
