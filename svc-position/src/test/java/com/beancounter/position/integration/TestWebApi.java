package com.beancounter.position.integration;

import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.position.service.Accumulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@SpringBootTest
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-md:+:stubs:8091")
@ActiveProfiles("test")
@Slf4j
class TestWebApi {

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
  }

  @Test
  @VisibleForTesting
  @Tag("slow")
  void is_MvcTradesToPositions() throws Exception {

    File tradeFile = new ClassPathResource("contracts/trades.json").getFile();

    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Transaction.class);

    Collection<Transaction> results = mapper.readValue(tradeFile, javaType);

    String json = mockMvc.perform(post("/")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(mapper.writeValueAsString(results))
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
  @VisibleForTesting
  @Tag("slow")
  void is_MvcValuingPositions() throws Exception {
    Asset asset = AssetUtils.getAsset("EBAY", "NASDAQ");
    Positions positions = new Positions(getPortfolio("TEST"));
    positions.setAsAt("2019-10-18");
    getPositions(asset, positions);
    PositionResponse positionResponse = PositionResponse.builder().data(positions).build();

    String json = mockMvc.perform(post("/value")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(mapper.writeValueAsString(positionResponse))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andReturn().getResponse().getContentAsString();

    PositionResponse fromJson = new ObjectMapper()
        .readValue(json, PositionResponse.class);

    assertThat(fromJson).isNotNull().hasFieldOrProperty("data");
    Positions mvcPositions = fromJson.getData();
    assertThat(mvcPositions).isNotNull();
    assertThat(mvcPositions.getPositions()).hasSize(positions.getPositions().size());
  }

  private void getPositions(Asset asset, Positions positions) {
    Transaction buy = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .portfolio(getPortfolio("TEST"))
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Accumulator accumulator = new Accumulator();

    Position position = accumulator.accumulate(buy, Position.builder().asset(asset).build());
    positions.add(position);
  }

  @Test
  @VisibleForTesting
  @Tag("slow")
  void is_MvcRestException() throws Exception {

    MvcResult result = mockMvc.perform(post("/value")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString("{asdf}"))
    ).andExpect(status().is4xxClientError()).andReturn();

    Optional<HttpMessageNotReadableException> someException =
        Optional.ofNullable((HttpMessageNotReadableException)
            result.getResolvedException());

    assertThat(someException.isPresent()).isTrue();

  }

}
