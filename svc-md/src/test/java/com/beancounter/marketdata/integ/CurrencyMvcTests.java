package com.beancounter.marketdata.integ;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.CurrencyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class CurrencyMvcTests {

  private static WireMockRule mockInternet;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(

    if (mockInternet == null) {
      mockInternet = new WireMockRule(options().port(7777));
      mockInternet.start();
    }
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

  }


  @Test
  void is_CurrencyDataReturning() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/currencies/")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType("application/json;charset=UTF-8"))
        .andReturn();

    CurrencyResponse currencyResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), CurrencyResponse.class);
    assertThat(currencyResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(currencyResponse.getData()).isNotEmpty();
  }

}
