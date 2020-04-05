package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.integ.TestRegistrationMvc.registerUser;
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
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.markets.MarketService;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
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
public class TrnControllerTest {
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;

  @Autowired
  private MarketService marketService;

  private MockMvc mockMvc;

  private Jwt token;
  private AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
        .apply(springSecurity())
        .build();
    SystemUser user = SystemUser.builder()
        .id("TrnMvcTest")
        .email("user@testing.com")
        .build();

    token = TokenUtils.getUserToken(user);
    registerUser(mockMvc, token, user);

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
            .with(jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body).isNotNull();

    TrnResponse trnResponse = objectMapper.readValue(body, TrnResponse.class);

    assertThat(trnResponse.getData()).isNotNull().hasSize(0);

  }


  @Test
  void is_PersistRetrieveAndPurge() throws Exception {

    Market nasdaq = marketService.getMarket("NASDAQ");

    Asset msft = asset(AssetRequest.builder()
        .data("msft", AssetUtils.getAsset(nasdaq, "MSFT"))
        .build());

    Asset aapl = asset(AssetRequest.builder()
        .data("aapl", AssetUtils.getAsset(nasdaq, "AAPL"))
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
            .batch("0")
            .provider("test")
            .callerId("1").build())
        .asset(msft.getId())
        .tradeDate(new DateUtils().getDate("2018-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .provider("test")
            .callerId("3").build())
        .asset(aapl.getId())
        .tradeDate(new DateUtils().getDate("2018-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());
    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .provider("test")
            .callerId("2").build())
        .asset(msft.getId())
        .tradeDate(new DateUtils().getDate("2017-01-01"))
        .quantity(BigDecimal.TEN)
        .price(BigDecimal.TEN)
        .tradeCurrency(nasdaq.getCurrency().getCode())
        .tradePortfolioRate(BigDecimal.ONE)
        .build());

    trnInputs.add(TrnInput.builder()
        .callerRef(CallerRef.builder()
            .batch("0")
            .provider("test")
            .callerId("4").build())
        .asset(aapl.getId())
        .tradeDate(new DateUtils().getDate("2017-01-01"))
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
            .with(jwt(token).authorities(authorityRoleConverter))
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

    // Find by Portfolio, sorted by assetId and then Date
    MvcResult mvcResult = mockMvc.perform(
        get("/trns/portfolio/{portfolioId}", portfolioId)
            .with(jwt(token).authorities(authorityRoleConverter))
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
            .with(jwt(token).authorities(authorityRoleConverter))
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
            .with(jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(findByAsset.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getData()).isNotEmpty().hasSize(2); // 2 MSFT transactions

    // Most recent transaction first
    assertThat(trnResponse.getData().iterator().next().getTradeDate()).isEqualTo("2018-01-01");

    // Purge all transactions for the Portfolio
    mvcResult = mockMvc.perform(
        delete("/trns/portfolio/{portfolioId}", portfolioId)
            .with(jwt(token).authorities(authorityRoleConverter))
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
            .with(jwt(token).authorities(authorityRoleConverter))
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
            .with(jwt(token).authorities(authorityRoleConverter))
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
