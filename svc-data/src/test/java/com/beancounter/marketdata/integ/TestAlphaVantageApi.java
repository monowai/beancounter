package com.beancounter.marketdata.integ;

import static com.beancounter.marketdata.utils.AlphaMockUtils.alphaContracts;
import static com.beancounter.marketdata.utils.AlphaMockUtils.mockAdjustedResponse;
import static com.beancounter.marketdata.utils.AlphaMockUtils.mockGlobalResponse;
import static com.beancounter.marketdata.utils.AlphaMockUtils.mockSearchResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.auth.server.AuthorityRoleConverter;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.MarketDataBoot;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.batch.ScheduledValuation;
import com.beancounter.marketdata.event.EventWriter;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.providers.PriceService;
import com.beancounter.marketdata.providers.alpha.AlphaConfig;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MarketDataService;
import com.beancounter.marketdata.service.MdFactory;
import com.beancounter.marketdata.utils.RegistrationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.Spy;
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
@SpringBootTest(classes = MarketDataBoot.class)
@ActiveProfiles("alpha")
@Tag("slow")
class TestAlphaVantageApi {

  private static WireMockRule alphaApi;
  private final AuthorityRoleConverter authorityRoleConverter = new AuthorityRoleConverter();
  private final DateUtils dateUtils = new DateUtils();
  @Autowired
  private MdFactory mdFactory;
  @Autowired
  private MarketService marketService;
  @Autowired
  private MarketDataService marketDataService;
  @Autowired
  private AlphaConfig alphaConfig;
  @Autowired
  private PriceService priceService;
  @Autowired
  private ScheduledValuation scheduledValuation;
  @Autowired
  private AssetService assetService;
  @Autowired
  private WebApplicationContext context;
  @Spy
  private EventWriter eventWriter;
  private MockMvc mockMvc;
  private Jwt token;

  @Autowired
  @SneakyThrows
  void mockServices() {
    if (alphaApi == null) {
      alphaApi = new WireMockRule(options().port(9999));
      alphaApi.start();
    }
    mockSearchResponse(alphaApi,
        "MSFT",
        new ClassPathResource(alphaContracts + "/msft-response.json").getFile());

    mockSearchResponse(alphaApi,
        "BRK-B",
        new ClassPathResource(alphaContracts + "/brkb-response.json").getFile());

    mockSearchResponse(alphaApi,
        "AAPL",
        new ClassPathResource(alphaContracts + "/appl-response.json").getFile());

    mockSearchResponse(alphaApi,
        "AMP.AX", // We search Alpha as AX
        new ClassPathResource(alphaContracts + "/amp-search.json").getFile());

    mockGlobalResponse(
        alphaApi, "AMP.AX",
        new ClassPathResource(alphaContracts + "/amp-global.json").getFile());

    mockGlobalResponse(
        alphaApi, "AMP.AUS",
        new ClassPathResource(alphaContracts + "/amp-global.json").getFile());

    mockGlobalResponse(
        alphaApi, "MSFT",
        new ClassPathResource(alphaContracts + "/msft-global.json").getFile());

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
  void is_PriceUpdated() throws Exception {
    marketDataService.purge();
    assetService.purge();
    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "ASX", "AMP")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = new ObjectMapper()
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    scheduledValuation.updatePrices();
    Thread.sleep(2000); // Async reads/writes
    PriceResponse price = marketDataService.getPriceResponse(assetResponse.getData());
    assertThat(price).hasNoNullFieldsOrProperties();

  }

  @Test
  void is_BrkBTranslated() throws Exception {
    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "NYSE", "BRK.B")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = new ObjectMapper()
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "BRK.B")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("priceSymbol", "BRK-B")
        .hasFieldOrPropertyWithValue("name", "Berkshire Hathaway Inc.");

    assertThat(alphaConfig.getPriceCode(assetResponse.getData())).isEqualTo("BRK-B");
  }

  @Test
  void is_MutualFundAssetEnrichedAndPriceReturned() throws Exception {

    mockSearchResponse(alphaApi, "B6WZJX0",
        new ClassPathResource("/contracts/alpha/mf-search.json").getFile());

    mockGlobalResponse(
        alphaApi, "0P0000XMSV.LON",
        new ClassPathResource(alphaContracts + "/pence-price-response.json").getFile());

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
        .hasFieldOrPropertyWithValue("market.code", "LON")
        .hasFieldOrPropertyWithValue("priceSymbol", "0P0000XMSV.LON")
        .hasFieldOrPropertyWithValue("name", "AXA Framlington Health Fund Z GBP Acc");
    assertThat(alphaConfig.getPriceCode(assetResponse.getData())).isEqualTo("0P0000XMSV.LON");
    PriceResponse priceResponse = marketDataService.getPriceResponse(assetResponse.getData());
    assertThat(priceResponse.getData().iterator().next())
        .isNotNull()
        .hasFieldOrPropertyWithValue("priceDate", dateUtils.getDate("2020-05-12"))
        .hasFieldOrPropertyWithValue("close", new BigDecimal("3.1620"))
        .hasFieldOrPropertyWithValue("previousClose", new BigDecimal("3.1620"))
        .hasFieldOrPropertyWithValue("low", new BigDecimal("3.1620"))
        .hasFieldOrPropertyWithValue("high", new BigDecimal("3.1620"))
        .hasFieldOrPropertyWithValue("open", new BigDecimal("3.1620"));

  }

  @Test
  void is_EnrichedMarketCodeTranslated() throws Exception {

    MvcResult mvcResult = mockMvc.perform(
        get("/assets/{market}/{code}", "ASX", "AMP")
            .with(jwt().jwt(token).authorities(authorityRoleConverter))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    AssetResponse assetResponse = new ObjectMapper()
        .readValue(mvcResult.getResponse().getContentAsString(), AssetResponse.class);

    assertThat(assetResponse.getData())
        .isNotNull()
        .hasFieldOrPropertyWithValue("code", "AMP")
        .hasFieldOrProperty("market")
        .hasFieldOrPropertyWithValue("priceSymbol", "AMP.AUS")
        .hasFieldOrPropertyWithValue("name", "AMP Limited");

    assertThat(alphaConfig.getPriceCode(assetResponse.getData())).isEqualTo("AMP.AUS");
  }

  @Test
  void is_ApiErrorMessageHandled() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/alphavantageError.json").getFile();

    mockGlobalResponse(alphaApi, "API.ERR", jsonFile);
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

    mockGlobalResponse(alphaApi, "API.KEY", jsonFile);
    Asset asset =
        Asset.builder().code("API")
            .market(Market.builder().code("KEY").build()).build();

    MarketDataProvider alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID);
    Collection<MarketData> results = alphaProvider.getMarketData(
        PriceRequest.of(asset).build());
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

    mockGlobalResponse(alphaApi, "ABC", jsonFile);
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
  void is_CurrentPriceFound() throws Exception {

    File jsonFile = new ClassPathResource(alphaContracts + "/global-response.json").getFile();

    mockGlobalResponse(alphaApi, "MSFT", jsonFile);

    Market nasdaq = Market.builder().code("NASDAQ").build();
    Asset asset = Asset.builder().code("MSFT").market(nasdaq).build();

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

  @Test
  void is_CurrentPriceAsxFound() {

    Market asx = Market.builder().code("ASX").build();
    Asset asset = Asset.builder().code("AMP").market(asx).build();

    PriceRequest priceRequest = PriceRequest.of(asset).build();
    Collection<MarketData> mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
        .getMarketData(priceRequest);

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

  @Test
  void is_PriceNotFoundSetIntoAsset() throws Exception {
    mockSearchResponse(alphaApi, "BWLD",
        new ClassPathResource("/contracts/alpha/bwld-search.json").getFile());

    mockGlobalResponse(alphaApi, "BWLD",
        new ClassPathResource("/contracts/alpha/price-not-found.json").getFile());
    AssetUpdateResponse result = assetService
        .process(AssetRequest.builder()
            .data("key", AssetInput.builder()
                .code("BWLD")
                .market("NYSE")
                .build()).build());

    Asset asset = result.getData().get("key");
    assertThat(asset.getPriceSymbol()).isEqualTo("UNLISTED");
    Optional<MarketData> priceResult = priceService.getMarketData(
        asset.getId(), dateUtils.getDate());

    assertThat(priceResult.isEmpty()).isTrue();

    PriceRequest priceRequest = PriceRequest.builder()
        .assets(Collections.singletonList(AssetUtils.getAssetInput(asset)))
        .build();
    PriceResponse priceResponse = marketDataService.getPriceResponse(priceRequest);
    assertThat(priceResponse).isNotNull().hasFieldOrProperty("data");
    MarketData price = priceResponse.getData().iterator().next();
    assertThat(price).hasFieldOrProperty("priceDate");

  }

  @Test
  void is_BackFillNasdaqIncludingDividendEvent() throws Exception {
    mockSearchResponse(alphaApi, "KMI",
        new ClassPathResource("/contracts/alpha/kmi-search.json").getFile());

    mockAdjustedResponse(alphaApi, "KMI",
        new ClassPathResource("/contracts/alpha/kmi-backfill-response.json").getFile());

    AssetUpdateResponse result = assetService
        .process(AssetRequest.builder()
            .data("key", AssetInput.builder()
                .code("KMI")
                .market("NASDAQ")
                .build()).build());

    Asset asset = result.getData().get("key");
    priceService.setEventWriter(eventWriter);
    marketDataService.backFill(asset);
    Thread.sleep(300);
    LocalDate date = dateUtils.getDate("2020-05-01");
    Optional<MarketData> marketData = priceService.getMarketData(asset.getId(), date);

    assertThat(marketData.isPresent());

    Mockito.verify(eventWriter, Mockito.times(1))
        .write(
            CorporateEvent.builder()
                .source("ALPHA")
                .trnType(TrnType.DIVI)
                .assetId(asset.getId())
                .recordDate(date)
                .rate(new BigDecimal("0.2625"))
                .split(new BigDecimal("1.0000"))
                .build()
        );
  }

}
