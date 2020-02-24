package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.integ.TestRegistrationMvc.registerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.AuthorityRoleConverter;
import com.beancounter.auth.JwtRoleConverter;
import com.beancounter.auth.TokenHelper;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.CurrencyUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.markets.MarketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
public class TrnMvcTest {
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private WebApplicationContext wac;

  @Autowired
  private MarketService marketService;

  private MockMvc mockMvc;

  private Jwt token;

  private AuthorityRoleConverter authorityRoleConverter
      = new AuthorityRoleConverter(new JwtRoleConverter());

  @Autowired
  void mockServices() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
        .apply(springSecurity())
        .build();
    SystemUser user = SystemUser.builder()
        .id("TrnMvcTest")
        .email("user@testing.com")
        .build();

    token = TokenHelper.getUserToken(user);
    registerUser(mockMvc, token, user);

  }

  @Test
  void is_PersistAndRetrieve() throws Exception {

    Market nasdaq = marketService.getMarket("NASDAQ");

    Asset msft = asset(AssetRequest.builder()
        .asset("msft", AssetUtils.getAsset("MSFT", nasdaq))
        .build());

    Asset aapl = asset(AssetRequest.builder()
        .asset("aapl", AssetUtils.getAsset("AAPL", nasdaq))
        .build());

    Portfolio portfolio = portfolio(Portfolio.builder()
        .code("Twix")
        .name("NZD Portfolio")
        .currency(CurrencyUtils.getCurrency("NZD"))
        .build());
    // Creating in random order and assert retrieved in Sort Order.
    TrnRequest trnRequest = TrnRequest.builder()
        .trn(Trn.builder()
            .id(TrnId.builder()
                .batch(0)
                .provider("test")
                .id(1).build())
            .portfolioId(portfolio.getId())
            .asset(msft)
            .tradeDate(DateUtils.getDate("2018-01-01"))
            .quantity(BigDecimal.TEN)
            .price(BigDecimal.TEN)
            .tradeCurrency(msft.getMarket().getCurrency())
            .tradePortfolioRate(BigDecimal.ONE)
            .build())
        .trn(Trn.builder()
            .id(TrnId.builder()
                .batch(0)
                .provider("test")
                .id(3).build())
            .portfolioId(portfolio.getId())
            .asset(aapl)
            .tradeDate(DateUtils.getDate("2018-01-01"))
            .quantity(BigDecimal.TEN)
            .price(BigDecimal.TEN)
            .tradeCurrency(msft.getMarket().getCurrency())
            .tradePortfolioRate(BigDecimal.ONE)
            .build())
        .trn(Trn.builder()
            .id(TrnId.builder()
                .batch(0)
                .provider("test")
                .id(2).build())
            .portfolioId(portfolio.getId())
            .asset(msft)
            .tradeDate(DateUtils.getDate("2017-01-01"))
            .quantity(BigDecimal.TEN)
            .price(BigDecimal.TEN)
            .tradeCurrency(msft.getMarket().getCurrency())
            .tradePortfolioRate(BigDecimal.ONE)
            .build())
        .trn(Trn.builder()
            .id(TrnId.builder()
                .batch(0)
                .provider("test")
                .id(4).build())
            .portfolioId(portfolio.getId())
            .asset(aapl)
            .tradeDate(DateUtils.getDate("2017-01-01"))
            .quantity(BigDecimal.TEN)
            .price(BigDecimal.TEN)
            .tradeCurrency(msft.getMarket().getCurrency())
            .tradePortfolioRate(BigDecimal.ONE)
            .build())
        .porfolioId(portfolio.getId())
        .build();

    MvcResult mvcResult = mockMvc.perform(
        post("/trns")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    TrnResponse trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getTrns()).isNotEmpty().hasSize(4);
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);

    String portfolioId = trnResponse.getTrns().iterator().next().getPortfolioId();
    assertThat(trnResponse.getPortfolios())
        .isNotEmpty()
        .hasAtLeastOneElementOfType(Portfolio.class);
    assertThat(portfolio).isEqualTo(trnResponse.getPortfolios().iterator().next());

    // Find by Portfolio, sorted by assetId and then Date
    mvcResult = mockMvc.perform(
        get("/trns/{portfolioId}", portfolioId)
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);
    assertThat(trnResponse.getTrns()).isNotEmpty().hasSize(4);

    int i = 4;
    // Verify the sort order - asset.code, tradeDate
    for (Trn trn : trnResponse.getTrns()) {
      assertThat(trn.getId().getId() == i--);
    }

    TrnId trnId = trnResponse.getTrns().iterator().next().getId();
    // Find by PrimaryKey
    mvcResult = mockMvc.perform(
        get("/trns/{portfolioId}/{provider}/{batch}/{id}",

            portfolioId, trnId.getProvider(), trnId.getBatch(), trnId.getId())
            .content(new ObjectMapper().writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
            .with(jwt(token).authorities(authorityRoleConverter))
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    trnResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), TrnResponse.class);
    assertThat(trnResponse.getTrns()).isNotEmpty().hasSize(1);
    assertThat(trnResponse.getPortfolios()).isNotEmpty().hasSize(1);

  }

  private Portfolio portfolio(Portfolio portfolio) throws Exception {

    PortfolioRequest createRequest = PortfolioRequest.builder()
        .data(Collections.singleton(portfolio))
        .build();

    MvcResult portfolioResult = mockMvc.perform(
        post("/portfolios")
            .with(jwt(token).authorities(authorityRoleConverter))
            .content(new ObjectMapper().writeValueAsBytes(createRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    PortfolioRequest portfolioResponse = objectMapper
        .readValue(portfolioResult.getResponse().getContentAsString(), PortfolioRequest.class);
    return portfolioResponse.getData().iterator().next();
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

    AssetResponse assetResponse = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    return assetResponse.getAssets().values().iterator().next();
  }
}
