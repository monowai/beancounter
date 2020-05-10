package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.utils.AlphaMockUtils.alphaContracts;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.providers.alpha.AlphaConfig;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MarketDataService;
import com.beancounter.marketdata.service.MdFactory;
import com.beancounter.marketdata.utils.AlphaMockUtils;
import com.beancounter.marketdata.utils.RegistrationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import java.util.Collection;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * .
 *
 * @author mikeh
 * @since 2019-03-04
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("alpha")
@Tag("slow")
class TestAlphaVantageApi {

  private static WireMockRule alphaApi;
  private final AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
  @Autowired
  private MdFactory mdFactory;
  @Autowired
  private MarketService marketService;
  @Autowired
  private MarketDataService marketDataService;
  @Autowired
  private AlphaConfig alphaConfig;
  @Autowired
  private WebApplicationContext context;
  private MockMvc mockMvc;
  private Jwt token;

  @Autowired
  @SneakyThrows
  void mockServices() {
    // ToDo: Figure out RandomPort + Feign.  Config issues :(
    if (alphaApi == null) {
      alphaApi = new WireMockRule(options().port(9999));
      alphaApi.start();
    }
    AlphaMockUtils.mockSearchResponse(alphaApi,
        "MSFT",
        new ClassPathResource("/contracts" + "/alpha/msft-response.json").getFile());

    AlphaMockUtils.mockSearchResponse(alphaApi,
        "AAPL",
        new ClassPathResource("/contracts" + "/alpha/appl-response.json").getFile());

    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();

    // Setup a user account
    SystemUser user = SystemUser.builder()
        .id("user")
        .email("user@testing.com")
        .build();
    token = TokenUtils.getUserToken(user);
    RegistrationUtils.registerUser(mockMvc, token);

  }

  @Test
  void is_MutualFundAssetEnriched() throws Exception {

    AlphaMockUtils.mockSearchResponse(alphaApi,
        "B6WZJX0",
        new ClassPathResource("/contracts" + "/alpha/mf-response.json").getFile());


    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "LON", "B6WZJX0")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = new ObjectMapper()
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "B6WZJX0")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("priceSymbol", "0P0000XMSV.LON")
        .hasFieldOrPropertyWithValue("name", "AXA Framlington Health Fund Z GBP Acc");
    assertThat(alphaConfig.getPriceCode(assetResponse.getData())).isEqualTo("0P0000XMSV.LON");
  }

  @Test
  void is_ApiErrorMessageHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageError.json").getFile();

    AlphaMockUtils.mockCurrentResponse(alphaApi, "API.ERR", jsonFile);
    Asset asset =
        Asset.builder().code("API").market(Market.builder().code("ERR").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    Collection<MarketData> results = alphaProvider.getMarketData(PriceRequest.of(asset).build());
    assertThat(results)
        .isNotNull()
        .hasSize(1);

    assertThat(results.iterator().next())
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", null);
  }

  @Test
  void is_ApiInvalidKeyHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageInfo.json").getFile();

    AlphaMockUtils.mockHistoricResponse(alphaApi, "API.KEY", jsonFile);
    Asset asset =
        Asset.builder().code("API")
            .market(Market.builder().code("KEY").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    Collection<MarketData> results = alphaProvider.getMarketData(
        PriceRequest.of(asset).date("2020-01-01").build());
    assertThat(results)
        .isNotNull()
        .hasSize(1);

    assertThat(results.iterator().next())
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", null);

  }

  @Test
  void is_ApiCallLimitExceededHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageNote.json").getFile();
    Market nasdaq = marketService.getMarket("NASDAQ");

    AlphaMockUtils.mockCurrentResponse(alphaApi, "ABC", jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(nasdaq).build();

    Collection<MarketData> results = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(PriceRequest
            .of(asset)
            .build());

    assertThat(results)
        .isNotNull()
        .hasSize(1);
    MarketData mdpPrice = results.iterator().next();
    assertThat(mdpPrice)
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", null);

    PriceResponse priceResponse = marketDataService.getPriceResponse(asset);
    assertThat(priceResponse).isNotNull();

  }

  @Test
  void is_SuccessHandledForAsx() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantage-asx.json").getFile();

    AlphaMockUtils.mockHistoricResponse(alphaApi, "ABC.AX", jsonFile);
    Asset asset =
        Asset.builder().code("ABC").market(Market.builder().code("ASX").build()).build();

    Collection<MarketData> mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(
            PriceRequest
                .of(asset)
                .date("2019-02-28")
                .build());

    MarketData marketData = mdResult.iterator().next();
    assertThat(marketData)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrPropertyWithValue("close", new BigDecimal("112.0300"))
        .hasFieldOrPropertyWithValue("open", new BigDecimal("112.0400"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("111.7300"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("112.8800"))
    ;

  }

  @Test
  void is_CurrentPriceFound() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/global-response.json").getFile();

    AlphaMockUtils.mockCurrentResponse(alphaApi, "MSFT", jsonFile);
    Market nasdaq = Market.builder().code("NASDAQ").build();
    Asset asset =
        Asset.builder().code("MSFT").market(nasdaq).build();
    PriceRequest priceRequest = PriceRequest.of(asset).build();
    Collection<MarketData> mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(priceRequest);

    // Coverage - WTD does not support this market
    assertThat(mdFactory.getMarketDataProvider(WtdService.ID).isMarketSupported(nasdaq)).isFalse();
    assertThat(mdFactory.getMarketDataProvider(WtdService.ID).isMarketSupported(null)).isFalse();

    MarketData marketData = mdResult.iterator().next();
    assertThat(marketData)
        .isNotNull()
        .hasFieldOrPropertyWithValue("asset", asset)
        .hasFieldOrProperty("close")
        .hasFieldOrProperty("open")
        .hasFieldOrProperty("low")
        .hasFieldOrProperty("high")
        .hasFieldOrProperty("previousClose")
        .hasFieldOrProperty("change");
  }
}
