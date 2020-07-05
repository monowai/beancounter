package com.beancounter.position.integration;

import static com.beancounter.common.utils.BcJson.getObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.server.RoleHelper;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
@Tag("slow")
@Slf4j
@SpringBootTest
class StubbedTrnValuations {

  @Autowired
  private WebApplicationContext wac;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
  }

  @SneakyThrows
  @Test
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_SingleAssetPosition() {
    DateUtils dateUtils = new DateUtils();
    Portfolio portfolio = new Portfolio(
        "TEST",
        "TEST",
        "NZD Portfolio",
        new Currency("NZD"),
        new Currency("USD"),
        null);

    TrustedTrnQuery query = new TrustedTrnQuery(
        portfolio,
        dateUtils.getDate("2020-05-01"),
        "KMI");

    String json = mockMvc.perform(post("/query")
        .content(getObjectMapper().writeValueAsBytes(query))
        .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andReturn().getResponse().getContentAsString();

    assertThat(json).isNotNull();
    PositionResponse positionResponse = getObjectMapper().readValue(json, PositionResponse.class);
    assertThat(positionResponse.getData()).isNotNull().hasFieldOrProperty("positions");
    assertThat(positionResponse.getData().getPositions()).hasSize(1);
    Position position = positionResponse.getData().getPositions().get("KMI:NYSE");
    assertThat(position).isNotNull();

  }

  @Test
  @SneakyThrows
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_PositionRequestFromTransactions() {

    String json = mockMvc.perform(get("/{portfolioCode}/2019-10-18", "TEST")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andReturn().getResponse().getContentAsString();

    PositionResponse positionResponse = getObjectMapper().readValue(json, PositionResponse.class);
    assertThat(positionResponse).isNotNull();
    assertThat(positionResponse.getData().getPortfolio())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "TEST");

    assertThat(positionResponse.getData().getAsAt()).isEqualTo("2019-10-18");

    assertThat(positionResponse.getData()
        .get(AssetUtils.getAsset("NASDAQ", "AAPL")))
        .isNotNull();

  }

  @SneakyThrows
  @Test
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_EmptyPortfolioPositionsReturned() {

    String json = mockMvc.perform(get("/{portfolioCode}/today", "EMPTY")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
    ).andExpect(
        status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andReturn().getResponse().getContentAsString();

    PositionResponse positionResponse = getObjectMapper().readValue(json, PositionResponse.class);

    assertThat(positionResponse).isNotNull();

    assertThat(positionResponse.getData().getPortfolio())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "EMPTY");

    assertThat(positionResponse.getData())
        .isNotNull();

    assertThat(positionResponse.getData().getPositions())
        .isNull();
  }
}
