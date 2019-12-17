package com.beancounter.marketdata.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.CurrencyUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Tag("slow")
@SpringBootTest
class PortfolioMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @Autowired
  void mockServices(WebApplicationContext wac) {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
  }

  @Test
  void is_findingByIdCode() throws Exception {
    Portfolio portfolio = Portfolio.builder()
        .code("Twix")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .build();

    PortfolioRequest createRequest = PortfolioRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioRequest = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(portfolioRequest)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfolioRequest.getData())
        .hasSize(1);

    String code = portfolioRequest.getData().iterator().next().getCode();
    String id = portfolioRequest.getData().iterator().next().getId();

    mockMvc.perform(
        get("/portfolios/{code}/code", "abc")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{code}/code", code)
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    mockMvc.perform(
        get("/portfolios/{id}", "invalidId")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().is4xxClientError())
        .andReturn();

    MvcResult mvcResult = mockMvc.perform(
        get("/portfolios")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest found = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(found.getData()).isNotEmpty();

  }

  @Test
  void is_persistAndFindPortfoliosWorking() throws Exception {
    Portfolio portfolio = Portfolio.builder()
        .code("TWEE")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .build();

    Collection<Portfolio> portfolios = new ArrayList<>();
    portfolios.add(portfolio);
    PortfolioRequest createRequest = PortfolioRequest.builder()
        .data(portfolios)
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioRequest = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);

    assertThat(portfolioRequest)
        .isNotNull()
        .hasFieldOrProperty("data");

    assertThat(portfolioRequest.getData())
        .hasSize(1);

    portfolioRequest.getData().forEach(foundPortfolio -> assertThat(foundPortfolio)
        .hasFieldOrProperty("id")
        .hasFieldOrPropertyWithValue("code", portfolio.getCode())
        .hasFieldOrPropertyWithValue("name",
            portfolio.getCurrency().getCode() + " Portfolio")
        .hasFieldOrPropertyWithValue("currency.code", "NZD")
        .hasFieldOrProperty("base"));

  }

}

