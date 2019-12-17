package com.beancounter.marketdata.integ;

import static com.beancounter.common.utils.AssetUtils.getAsset;
import static com.beancounter.marketdata.EcbMockUtils.get;
import static com.beancounter.marketdata.EcbMockUtils.getRateMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.PortfolioRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.WtdMockUtils;
import com.beancounter.marketdata.controller.FxController;
import com.beancounter.marketdata.controller.MarketController;
import com.beancounter.marketdata.controller.PriceController;
import com.beancounter.marketdata.currency.CurrencyController;
import com.beancounter.marketdata.portfolio.PortfolioController;
import com.beancounter.marketdata.portfolio.PortfolioService;
import com.beancounter.marketdata.providers.fxrates.EcbRates;
import com.beancounter.marketdata.providers.fxrates.FxGateway;
import com.beancounter.marketdata.providers.wtd.WtdGateway;
import com.beancounter.marketdata.providers.wtd.WtdResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Spring Contract base class.  Mocks out calls to various gateways that can be imported
 * and run as stubs in other services.  Any data required from an integration call in a
 * dependent service, should be mocked in this class.
 *
 * <p>WireMock is used within svc-data to test Feign based gateway integration.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@AutoConfigureMessageVerifier
@AutoConfigureWireMock(port = 0)
public class ContractVerifierBase {

  static Asset aapl = getAsset("AAPL", "NASDAQ");
  static Asset msft = getAsset("MSFT", "NASDAQ");
  static Asset msftInvalid = getAsset("MSFTx", "NASDAQ");
  static Asset amp = getAsset("AMP", "ASX");
  private static Asset ebay = getAsset("EBAY", "NASDAQ");
  @Autowired
  private FxController fxController;
  @Autowired
  private PriceController priceController;
  @Autowired
  private MarketController marketController;
  @Autowired
  private CurrencyController currencyController;
  @Autowired
  private PortfolioController portfolioController;

  @MockBean
  PortfolioService portfolioService;

  @MockBean
  private FxGateway fxGateway;

  @MockBean
  private WtdGateway wtdGateway;

  @Before
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
    mockEcbRates(rates, get(DateUtils.today(), rates));

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

    RestAssuredMockMvc.standaloneSetup(MockMvcBuilders
        .standaloneSetup(fxController,
            priceController,
            marketController,
            currencyController,
            portfolioController));

  }

  @Before
  public void mockWtd() {
    // WTD Price Mocking
    // Ebay
    Map<String, MarketData> results = new HashMap<>();
    String date = "2019-10-18";

    results.put("EBAY", WtdMockUtils.get(date, ebay,
        "39.21", "100.00", "39.35", "38.74", "6274307"));
    mockWtdResponse(String.join(",", results.keySet()), date, WtdMockUtils.get(date, results));

  }

  @Before
  public void mockPortfolios() throws Exception {
    File jsonFile = new ClassPathResource("contracts/portfolio/TestResponse.json").getFile();
    PortfolioRequest portfolioRequest = new ObjectMapper().readValue(jsonFile, PortfolioRequest.class);
    Portfolio portfolio = portfolioRequest.getData().iterator().next();
    // For the sake of convenience when testing; id and code are the same
    Mockito.when(portfolioService.find("TEST"))
        .thenReturn(portfolio);

    Mockito.when(portfolioService.findByCode("TEST"))
        .thenReturn(portfolio);

  }

  private void mockEcbRates(Map<String, BigDecimal> rates, EcbRates ecbRates) {
    mockEcbRates(rates, ecbRates, DateUtils.getDate(ecbRates.getDate()));
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
    assertThat(fxController).isNotNull();
    assertThat(wtdGateway).isNotNull();
    assertThat(fxGateway).isNotNull();
  }
}