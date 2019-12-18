package com.beancounter.position.integration;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.position.service.Accumulator;
import com.beancounter.position.service.BcService;
import com.beancounter.position.service.Valuation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
@Tag("slow")
@Slf4j
@SpringBootTest(properties = "stubrunner.cloud.enabled=false")
class StubbedFxValuations {

  @Autowired
  private WebApplicationContext wac;
  private MockMvc mockMvc;

  @Autowired
  private Valuation valuation;

  @Autowired
  private BcService bcService;

  private ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
  }

  private Positions getPositions(Asset asset) {

    Transaction transaction = Transaction.builder()
        .trnType(TrnType.BUY)
        .asset(asset)
        .tradeAmount(new BigDecimal(2000))
        .quantity(new BigDecimal(100)).build();

    Portfolio portfolio = bcService.getPortfolioByCode("TEST");
    transaction.setTradeCurrency(
        bcService.getCurrency(asset.getMarket().getCurrency()));

    Accumulator accumulator = new Accumulator();
    Positions positions = new Positions(portfolio);
    positions.setAsAt("2019-10-18");

    Position position = accumulator.accumulate(transaction, portfolio,
        Position.builder().asset(asset).build());

    positions.add(position);
    return positions;
  }

  private Positions getValuedPositions(Asset asset) {
    Positions positions = getPositions(asset);
    assertThat(positions).isNotNull();
    assertThat(valuation).isNotNull();
    valuation.value(positions);
    return positions;
  }

  @Test
  void are_MarketsFound() {
    MarketResponse markets = bcService.getMarkets();
    assertThat(markets).isNotNull();
    assertThat(markets.getData()).isNotEmpty();
  }

  @Test
  void is_MvcTradesToPositions() throws Exception {

    File tradeFile = new ClassPathResource("contracts/trades.json").getFile();

    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Transaction.class);

    Collection<Transaction> results = mapper.readValue(tradeFile, javaType);
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId("TEST")
        .transactions(results).build();

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
  void is_MvcValuingPositions() throws Exception {
    Asset asset = AssetUtils.getAsset("EBAY", "NASDAQ");
    Positions positions = getPositions(asset);
    PositionResponse positionResponse = PositionResponse.builder().data(positions).build();

    assertThat(mockMvc).isNotNull();
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

  @Test
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

  @Test
  void is_MarketValuationCalculatedAsAt() {
    Asset asset = AssetUtils.getAsset("EBAY",
        Market.builder().code("NASDAQ")
            .currency(getCurrency("USD"))
            .build()
    );

    // We need to have a Quantity in order to get the price, so create a position
    Positions positions = getValuedPositions(asset);

    assertThat(positions.get(asset).getMoneyValues(Position.In.TRADE))
        .hasFieldOrPropertyWithValue("unrealisedGain", new BigDecimal("8000.00"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("100.00"))
        .hasFieldOrPropertyWithValue("marketValue", new BigDecimal("10000.00"))
        .hasFieldOrPropertyWithValue("totalGain", new BigDecimal("8000.00"))
    ;
  }

  @Test
  void is_ZeroHoldingsSafe() {

    Positions positions = new Positions(Portfolio.builder().code("TEST").build());
    valuation.value(positions);
    assertThat(positions.getPositions()).isEmpty();

  }

  @Test
  void is_AssetAndCurrencyHydratedFromValuationRequest() {

    Asset asset = AssetUtils.getAsset("EBAY", "NASDAQ");

    Positions positions = getValuedPositions(asset);
    Position position = positions.get(asset);
    assertThat(position)
        .hasFieldOrProperty("asset");

    assertThat(position.getAsset().getMarket())
        .hasNoNullFieldsOrPropertiesExcept("aliases", "currencyId", "timezoneId", "currencyCode");

    assertThat(position.getMoneyValues().get(Position.In.PORTFOLIO).getCurrency())
        .hasNoNullFieldsOrProperties();
    assertThat(position.getMoneyValues().get(Position.In.BASE).getCurrency())
        .hasNoNullFieldsOrProperties();
    assertThat(position.getMoneyValues().get(Position.In.TRADE).getCurrency())
        .hasNoNullFieldsOrProperties();

  }


}
