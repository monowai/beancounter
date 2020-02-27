package com.beancounter.position.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.RoleHelper;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.util.Collection;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.core.io.ClassPathResource;
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

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
  }

  @SneakyThrows
  @Test
  @WithMockUser(username = "test-user", roles = {RoleHelper.OAUTH_USER})
  void is_MvcTradesToPositions() {

    File tradeFile = new ClassPathResource("contracts/trades.json").getFile();
    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Trn.class);

    Collection<Trn> results = mapper.readValue(tradeFile, javaType);
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId("TEST")
        .trns(results).build();

    String json = mockMvc.perform(post("/")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(mapper.writeValueAsString(positionRequest))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andReturn().getResponse().getContentAsString();

    PositionResponse positionResponse = new ObjectMapper().readValue(json, PositionResponse.class);
    assertThat(positionResponse).hasFieldOrProperty("data");
    Positions positions = positionResponse.getData();

    assertThat(positions).isNotNull();
    assertThat(positions.getPositions()).isNotNull().hasSize(2);
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

    PositionResponse positionResponse = mapper.readValue(json, PositionResponse.class);
    assertThat(positionResponse).isNotNull();
    assertThat(positionResponse.getData().getPortfolio())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "TEST");

    assertThat(positionResponse.getData().getAsAt()).isEqualTo("2019-10-18");

    assertThat(positionResponse.getData()
        .get(AssetUtils.getAsset("AAPL", "NASDAQ")))
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

    PositionResponse positionResponse = mapper.readValue(json, PositionResponse.class);

    assertThat(positionResponse).isNotNull();

    assertThat(positionResponse.getData().getPortfolio())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "EMPTY");

    assertThat(positionResponse.getData())
        .isNotNull();

    assertThat(positionResponse.getData().getPositions())
        .isNotNull()
        .isEmpty();
  }
}
