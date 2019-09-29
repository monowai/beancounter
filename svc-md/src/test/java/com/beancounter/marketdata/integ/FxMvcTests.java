package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxPairResults;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.FxResults;
import com.beancounter.common.request.FxRequest;
import com.beancounter.marketdata.DataProviderUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
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
        "/2019-08-27?base=USD&symbols=AUD,EUR,GBP,USD,NZD",
        rateResponse);
    String date = "2019-08-27";
    CurrencyPair nzdUsd = CurrencyPair.builder().from("NZD").to("USD").build();
    CurrencyPair usdNzd = CurrencyPair.builder().from("USD").to("NZD").build();
    CurrencyPair usdUsd = CurrencyPair.builder().from("USD").to("USD").build();
    CurrencyPair nzdNzd = CurrencyPair.builder().from("NZD").to("NZD").build();

    Collection<CurrencyPair> pairs = new ArrayList<>();
    pairs.add(nzdUsd);
    pairs.add(usdNzd);
    pairs.add(nzdNzd);
    pairs.add(usdUsd);

    ObjectMapper objectMapper = new ObjectMapper();

    FxRequest fxRequest = FxRequest.builder().rateDate(date).pairs(pairs).build();
    MvcResult mvcResult = mockMvc.perform(
        post("/fx")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(fxRequest)
            )
    ).andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andReturn();

    FxResults fxResults = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), FxResults.class);

    FxPairResults results = fxResults.getData().get(date);
    assertThat(results.getRates()).isNotNull().hasSize(pairs.size());
    Map<CurrencyPair, FxRate> theRates = results.getRates();
    assertThat(theRates)
        .containsKeys(nzdUsd, usdNzd);

    for (CurrencyPair currencyPair : theRates.keySet()) {
      assertThat(results.getRates().get(currencyPair).getDate()).isNotNull();
    }

  }

}
