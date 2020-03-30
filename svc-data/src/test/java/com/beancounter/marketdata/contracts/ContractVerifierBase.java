package com.beancounter.marketdata.contracts;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.marketdata.utils.EcbMockUtils.get;
import static com.beancounter.marketdata.utils.EcbMockUtils.getRateMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.auth.common.TokenUtils;
import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.contracts.PortfolioResponse;
import com.beancounter.common.contracts.PortfoliosRequest;
import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.contracts.RegistrationResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.providers.fxrates.EcbRates;
import com.beancounter.marketdata.providers.fxrates.FxGateway;
import com.beancounter.marketdata.providers.wtd.WtdGateway;
import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.beancounter.marketdata.registration.SystemUserService;
import com.beancounter.marketdata.trn.TrnService;
import com.beancounter.marketdata.utils.WtdMockUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Spring Contract base class.  Mocks out calls to various gateways that can be imported
 * and run as stubs in other services.  Any data required from an integration call in a
 * dependent service, should be mocked in this class.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext
@AutoConfigureMessageVerifier
@AutoConfigureWireMock(port = 0)
@WebAppConfiguration
@ActiveProfiles("nosecurity")
public class ContractVerifierBase {

  public static final Asset AAPL = getAsset("AAPL", "NASDAQ");
  public static final Asset MSFT = getAsset("MSFT", "NASDAQ");
  public static final Asset MSFT_INVALID = getAsset("MSFTx", "NASDAQ");
  public static final Asset AMP = getAsset("AMP", "ASX");
  public static final Asset EBAY = getAsset("EBAY", "NASDAQ");
  @MockBean
  JwtDecoder jwtDecoder;
  @MockBean
  private FxGateway fxGateway;
  @MockBean
  private WtdGateway wtdGateway;
  @MockBean
  private PortfolioService portfolioService;
  @MockBean
  private TrnService trnService;
  @MockBean
  private AssetService assetService;
  @MockBean
  private SystemUserService systemUserService;
  @Autowired
  private WebApplicationContext context;
  private String contractPath = "contracts";

  @BeforeEach
  public void initMocks() {
    MockMvc mockMvc = MockMvcBuilders
        .webAppContextSetup(context)
        .build();

    RestAssuredMockMvc
        .mockMvc(mockMvc);

  }

  @BeforeEach
  @SneakyThrows
  public void systemUsers() {
    ObjectMapper objectMapper = new ObjectMapper();
    File jsonFile =
        new ClassPathResource(contractPath + "/register/response.json").getFile();
    RegistrationResponse response = objectMapper.readValue(jsonFile, RegistrationResponse.class);
    String email = "blah@blah.com";

    Jwt jwt = TokenUtils.getUserToken(SystemUser.builder()
        .id(email)
        .email(email)
        .build());

    Mockito.when(jwtDecoder.decode(email)).thenReturn(jwt);

    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwtDecoder.decode(email)));

    Mockito.when(systemUserService.register(jwt)).thenReturn(response);

  }

  @BeforeEach
  public void ecbRates() {
    Map<String, BigDecimal> rates;
    rates = getRateMap("0.8973438622", "1.3652189519", "0.7756191673",
        "1.5692749462", "1.4606963388");

    mockEcbRates(rates, get("2019-10-20", rates));
    rates = getRateMap("0.8973438622", "1.3652189519", "0.7756191673",
        "10.0", "1.4606963388");
    mockEcbRates(rates, get("2019-01-01", rates));

    rates = getRateMap("0.8973438622", "1.3652189519", "0.7756191673",
        "1.5692749462", "1.4606963388");
    mockEcbRates(rates, get("2019-10-18", rates));

    // Current
    mockEcbRates(rates, get(new DateUtils().today(), rates));

    rates = getRateMap("0.897827258", "1.3684683067", "0.8047495062",
        "1.5053869635", "1.4438857964");
    mockEcbRates(rates, get("2019-07-26", rates));
    // Saturday results are the same as Fridays
    mockEcbRates(rates, get("2019-07-26", rates), "2019-07-27");

    rates = getRateMap("0.9028530155", "1.3864210906", "0.8218941856",
        "1.5536294691", "1.4734561213");
    mockEcbRates(rates, get("2019-08-16", rates));

    rates = getRateMap("0.9078529278", "1.36123468", "0.7791193827",
        "1.5780299591", "1.460463005");
    mockEcbRates(rates, get("2019-11-12", rates));

    rates = getRateMap("0.8482483671", "1.6586648571", "0.6031894139",
        "1.8855712953", "1.6201543812");
    mockEcbRates(rates, get("1999-01-04", rates));

  }

  @BeforeEach
  public void wtdPrices() {
    // WTD Price Mocking
    // Ebay
    Map<String, MarketData> results = new HashMap<>();
    String date = "2019-10-18";

    results.put("EBAY", WtdMockUtils.get(date, EBAY,
        "39.21", "100.00", "39.35", "38.74", "6274307"));
    mockWtdResponse(String.join(",", results.keySet()), date,
        WtdMockUtils.get(date, results));

  }

  @BeforeEach
  public void mockTrnGetResponse() throws Exception {
    mockTrnPostResponse(getTestPortfolio(), contractPath + "/trn/CSV-write.json",
        contractPath + "/trn/CSV-response.json");
    mockTrnGetResponse(getTestPortfolio(), contractPath + "/trn/TEST-response.json");
    mockTrnGetResponse(getEmptyPortfolio(), contractPath + "/trn/EMPTY-response.json");
  }

  @SneakyThrows
  void mockTrnGetResponse(Portfolio portfolio, String trnFile) {
    File jsonFile = new ClassPathResource(trnFile).getFile();
    TrnResponse trnResponse = new ObjectMapper().readValue(jsonFile, TrnResponse.class);
    Mockito.when(trnService.find(portfolio))
        .thenReturn(trnResponse);
  }

  @SneakyThrows
  void mockTrnPostResponse(Portfolio portfolio, String requestFile, String responseFile) {
    File jsonFile = new ClassPathResource(requestFile).getFile();
    TrnRequest trnRequest = new ObjectMapper().readValue(jsonFile, TrnRequest.class);
    jsonFile = new ClassPathResource(responseFile).getFile();
    TrnResponse trnResponse = new ObjectMapper().readValue(jsonFile, TrnResponse.class);
    Mockito.when(trnService.save(portfolio, trnRequest))
        .thenReturn(trnResponse);
  }

  @BeforeEach
  @SneakyThrows
  public void mockPortfolios() {
    ObjectMapper objectMapper = new ObjectMapper();
    mockPortfolio(getEmptyPortfolio());
    mockPortfolio(getTestPortfolio());
    // All Portfolio
    File jsonFile =
        new ClassPathResource(contractPath + "/portfolio/portfolios.json").getFile();
    PortfoliosResponse response = objectMapper.readValue(jsonFile, PortfoliosResponse.class);
    Mockito.when(portfolioService.getPortfolios()).thenReturn(response.getData());

    jsonFile =
        new ClassPathResource(contractPath + "/portfolio/add-request.json").getFile();
    PortfoliosRequest portfoliosRequest =
        objectMapper.readValue(jsonFile, PortfoliosRequest.class);

    jsonFile =
        new ClassPathResource(contractPath + "/portfolio/add-response.json").getFile();
    PortfoliosResponse portfoliosResponse =
        objectMapper.readValue(jsonFile, PortfoliosResponse.class);
    Mockito.when(portfolioService.save(portfoliosRequest.getData()))
        .thenReturn(portfoliosResponse.getData());
  }

  private Portfolio getTestPortfolio() throws IOException {
    File jsonFile =
        new ClassPathResource(contractPath + "/portfolio/test.json").getFile();

    return getPortfolio(jsonFile);
  }

  private Portfolio getEmptyPortfolio() throws IOException {
    File jsonFile =
        new ClassPathResource(contractPath + "/portfolio/empty.json").getFile();

    return getPortfolio(jsonFile);
  }

  private Portfolio getPortfolio(File jsonFile) throws IOException {
    PortfolioResponse portfolioResponse =
        new ObjectMapper().readValue(jsonFile, PortfolioResponse.class);
    return portfolioResponse.getData();
  }

  private void mockPortfolio(Portfolio portfolio) {
    // For the sake of convenience when testing; id and code are the same
    Mockito.when(portfolioService.find(portfolio.getId()))
        .thenReturn(portfolio);

    Mockito.when(portfolioService.findByCode(portfolio.getCode()))
        .thenReturn(portfolio);
  }

  @BeforeEach
  public void mockAssets() throws Exception {
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/response.json").getFile());
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/msft-request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/msft-response.json").getFile());
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/bhp-request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/bhp-response.json").getFile());
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/bhp-lse-request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/bhp-lse-response.json").getFile());
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/abbv-request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/abbv-response.json").getFile());
    mockAssetResponse(
        new ClassPathResource(contractPath + "/assets/amp-request.json").getFile(),
        new ClassPathResource(contractPath + "/assets/amp-response.json").getFile());

  }

  @SneakyThrows
  private void mockAssetResponse(File jsonRequest, File jsonResponse) {
    AssetRequest assetRequest =
        new ObjectMapper().readValue(jsonRequest, AssetRequest.class);

    AssetUpdateResponse assetUpdateResponse =
        new ObjectMapper().readValue(jsonResponse, AssetUpdateResponse.class);

    Mockito.when(assetService.process(assetRequest)).thenReturn(assetUpdateResponse);
  }

  private void mockEcbRates(Map<String, BigDecimal> rates, EcbRates ecbRates) {
    mockEcbRates(rates, ecbRates, new DateUtils().getDateString(ecbRates.getDate()));
  }

  private void mockEcbRates(Map<String, BigDecimal> rates, EcbRates ecbRates, String rateDate) {
    Mockito.when(fxGateway.getRatesForSymbols(rateDate, "USD",
        String.join(",", rates.keySet())))
        .thenReturn(ecbRates);
  }

  private void mockWtdResponse(String assets, String date, WtdResponse wtdResponse) {
    Mockito.when(wtdGateway.getMarketDataForAssets(assets, date, "demo"))
        .thenReturn(wtdResponse);
  }

  @Test
  public void is_Started() {
    assertThat(wtdGateway).isNotNull();
    assertThat(fxGateway).isNotNull();
    assertThat(assetService).isNotNull();
    assertThat(trnService).isNotNull();
    assertThat(portfolioService).isNotNull();
  }
}