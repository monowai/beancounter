package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class MarketMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

  }

  @Test
  void is_AllMarketsFound() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/markets/")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    MarketResponse marketResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), MarketResponse.class);
    assertThat(marketResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(marketResponse.getData()).isNotEmpty();
  }

  @Test
  void is_SingleMarketFoundCaseInsensitive() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/markets/nzx")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    MarketResponse marketResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), MarketResponse.class);
    assertThat(marketResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(marketResponse.getData()).hasSize(1);

    Market nzx = marketResponse.getData().iterator().next();
    assertThat(nzx).hasNoNullFieldsOrPropertiesExcept("currencyId");
  }

  @Test
  void is_SingleMarketBadRequest() throws Exception {
    mockMvc.perform(
        get("/markets/non-existent")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError());

  }

}
