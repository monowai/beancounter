package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.utils.RegistrationUtils.registerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.MarketDataBoot;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.trn.TrnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = MarketDataBoot.class)
@ActiveProfiles("test")
@Tag("slow")
public class TrnControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
  private final DateUtils dateUtils = new DateUtils();
  @Autowired
  private WebApplicationContext wac;
  @Autowired
  private MarketService marketService;
  @Autowired
  private PortfolioService portfolioService;
  @Autowired
  private CurrencyService currencyService;
  @Autowired
  private TrnService trnService;
  private MockMvc mockMvc;
  @MockBean
  private FigiProxy figiProxy;
  private Jwt token;

  @Autowired
  void mockServices() {
    assertThat(currencyService.getCurrencies()).isNotEmpty();
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
        .apply(springSecurity())
        .build();
    SystemUser user = SystemUser.builder()
        .id("TrnMvcTest")
        .email("user@testing.com")
        .build();

    token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token);
    assertThat(figiProxy).isNotNull();
  }

  @Test
  void is_EmptyResponseValid() throws Exception {
    Portfolio portfolio = portfolio(PortfolioInput.builder()
        .code("BLAH")
        .name("NZD Portfolio")
        .currency("NZD")
        .build());

    MvcResult mvcResult = mockMvc.perform(
        get("/trns/portfolio/{portfolioId}", portfolio.getId())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).isNotNull();

    TrnResponse trnResponse = objectMapper.readValue(body, TrnResponse.class);

    assertThat(trnResponse.getData()).isNotNull().hasSize(0);

  }

  @Test
  void is_ExistingDividendFound() throws Exception {
    Market nasdaq = marketService.getMarket("NASDAQ");

    Asset msft = asset(AssetRequest.builder()
        .data("msft", AssetUtils.getAssetInput(nasdaq.getCode(), "MSFT"))
        .build());

    Portfolio portfolioA = portfolio(PortfolioInput.builder()
        .code("DIV-TEST")
        .name("NZD Portfolio")
        .currency("NZD")
        .build());

    // Creating in random order and assert retrieved in Sort Order.
    Collection<TrnInput> existingTrns = new ArrayList<>();
    existingTrns.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("DIV-TEST")
            .callerId("1").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2020-03-10"))
        .trnType(TrnType.DIVI)
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());

    TrnRequest trnRequest = TrnRequest.builder()
        .data(existingTrns)
        .portfolioId(portfolioA.getId())
        .build();

    trnService.save(portfolioA, trnRequest);
    TrnInput proposedDivi = existingTrns.iterator().next();

    TrustedTrnEvent trustedTrnEvent = TrustedTrnEvent.builder()
        .portfolio(portfolioA)
        .trnInput(proposedDivi)
        .build();

    assertThat(trnService.isExists(trustedTrnEvent))
        .isTrue();

    // Record date is earlier than an existing trn trade date
    proposedDivi.setTradeDate(dateUtils.getDate("2020-02-25"));
    assertThat(trnService.isExists(trustedTrnEvent))
        .isTrue(); // Within 20 days of proposed trade date

    proposedDivi.setTradeDate(dateUtils.getDate("2020-03-09"));
    assertThat(trnService.isExists(trustedTrnEvent))
        .isTrue(); // Within 20 days of proposed trade date

  }

  @Test
  void is_TrnForPortfolioInRangeFound() throws Exception {
    Market nasdaq = marketService.getMarket("NASDAQ");

    Asset msft = asset(AssetRequest.builder()
        .data("msft", AssetUtils.getAssetInput(nasdaq.getCode(), "MSFT"))
        .build());

    Portfolio portfolioA = portfolio(PortfolioInput.builder()
        .code("PFA")
        .name("NZD Portfolio")
        .currency("NZD")
        .build());

    // Creating in random order and assert retrieved in Sort Order.
    Collection<TrnInput> trnInputs = new ArrayList<>();
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .callerId("1").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2018-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .callerId("2").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2016-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());

    TrnRequest trnRequest = TrnRequest.builder()
        .data(trnInputs)
        .portfolioId(portfolioA.getId())
        .build();

    mockMvc.perform(
        post("/trns")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnInputs.clear();
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .callerId("3").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2018-10-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .callerId("4").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2017-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());

    Portfolio portfolioB = portfolio(PortfolioInput.builder()
        .code("PFB")
        .name("NZD Portfolio")
        .currency("NZD")
        .build());

    trnRequest = TrnRequest.builder()
        .data(trnInputs)
        .portfolioId(portfolioB.getId())
        .build();

    mockMvc.perform(
        post("/trns")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    // All transactions are now in place.

    MvcResult response = mockMvc.perform(
        get("/portfolios/asset/{assetId}/{tradeDate}",
            msft.getId(), "2018-01-01")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();


    PortfoliosResponse portfolios = objectMapper
        .readValue(response.getResponse().getContentAsString(), PortfoliosResponse.class);

    assertThat(portfolios.getData()).hasSize(2);
    portfolios = portfolioService.findWhereHeld(msft.getId(), dateUtils.getDate("2016-01-01"));
    assertThat(portfolios.getData()).hasSize(1);
    portfolios = portfolioService.findWhereHeld(msft.getId(), null);
    assertThat(portfolios.getData()).hasSize(2);

  }

  @Test
  void is_PersistRetrieveAndPurge() throws Exception {

    Market nasdaq = marketService.getMarket("NASDAQ");

    Asset msft = asset(AssetRequest.builder()
        .data("msft", AssetUtils.getAssetInput(nasdaq.getCode(), "MSFT"))
        .build());

    Asset aapl = asset(AssetRequest.builder()
        .data("aapl", AssetUtils.getAssetInput(nasdaq.getCode(), "AAPL"))
        .build());

    Portfolio portfolio = portfolio(PortfolioInput.builder()
        .code("Twix")
        .name("NZD Portfolio")
        .currency("NZD")
        .build());
    // Creating in random order and assert retrieved in Sort Order.
    Collection<TrnInput> trnInputs = new ArrayList<>();
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .callerId("1").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2018-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .callerId("3").build())
        .asset(aapl.getId())
        .tradeDate(dateUtils.getDate("2018-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .callerId("2").build())
        .asset(msft.getId())
        .tradeDate(dateUtils.getDate("2017-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());

    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .callerId("4").build())
        .asset(aapl.getId())
        .tradeDate(dateUtils.getDate("2017-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    TrnRequest trnRequest = TrnRequest.builder()
        .data(trnInputs)
        .portfolioId(portfolio.getId())
        .build();

    MvcResult postResult = mockMvc.perform(
        post("/trns")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    TrnResponse trnResponse = objectMapper
        .readValue(postResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getData()).isNotEmpty().hasSize(4);
    for (Trn trn : trnResponse.getData()) {
      assertThat(trn.getAsset()).isNotNull();
    }

    String portfolioId = trnResponse.getData().iterator().next().getPortfolio().getId();
    //trnService.find()
    // Find by Portfolio, sorted by assetId and then Date
    MvcResult mvcResult = mockMvc.perform(
        get("/trns/portfolio/{portfolioId}", portfolioId)
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getData()).isNotEmpty().hasSize(4);

    int i = 4;
    // Verify the sort order - asset.code, tradeDate
    for (Trn trn : trnResponse.getData()) {
      assertThat(trn.getCallerRef().getCallerId().equals(String.valueOf(i--)));
      assertThat(trn.getAsset()).isNotNull();
      assertThat(trn.getId()).isNotNull();
    }

    Trn trn = trnResponse.getData().iterator().next();
    // Find by PrimaryKey
    mvcResult = mockMvc.perform(
        get("/trns/{portfolioId}/{trnId}",
            portfolio.getId(),
            trn.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getData()).isNotEmpty().hasSize(1);

    // Find by portfolio and asset
    MvcResult findByAsset = mockMvc.perform(
        get("/trns/{portfolioId}/asset/{assetId}",
            portfolioId, msft.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(findByAsset.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getData()).isNotEmpty().hasSize(2); // 2 MSFT transactions

    // Most recent transaction first (display purposes
    assertThat(trnResponse.getData().iterator().next().getTradeDate()).isEqualTo("2018-01-01");

    // Purge all transactions for the Portfolio
    mvcResult = mockMvc.perform(
        delete("/trns/portfolio/{portfolioId}", portfolioId)
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).isNotNull().isEqualTo("4");
  }

  private Portfolio portfolio(PortfolioInput portfolio) throws Exception {

    PortfoliosRequest createRequest = PortfoliosRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios", portfolio.getCode())
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfoliosResponse portfoliosResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfoliosResponse.class);
    return portfoliosResponse.getData().iterator().next();
  }

  private Asset asset(AssetRequest assetRequest) throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        post("/assets/")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(assetRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetUpdateResponse assetUpdateResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetUpdateResponse.class);

    return assetUpdateResponse.getData().values().iterator().next();
  }
}
