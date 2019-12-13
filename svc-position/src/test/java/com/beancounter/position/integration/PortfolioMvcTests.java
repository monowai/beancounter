package com.beancounter.position.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.PortfolioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-md:+:stubs:10999")
@ActiveProfiles("test")
@Tag("slow")
class PortfolioMvcTests {

  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

  }


  @Test
  void is_PortfolioDataReturning() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/portfolios")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioResponse portfolioResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), PortfolioResponse.class);
    assertThat(portfolioResponse).isNotNull().hasFieldOrProperty("data");
    assertThat(portfolioResponse.getData()).isNotEmpty();
  }

}
