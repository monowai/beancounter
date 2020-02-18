package com.beancounter.shell.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxPairResults;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.reader.Transformer;
import com.beancounter.shell.service.FxRateService;
import com.beancounter.shell.service.FxTransactions;
import com.beancounter.shell.service.StaticService;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTrades;
import com.beancounter.shell.sharesight.ShareSightTransformers;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

@Tag("slow")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@ActiveProfiles("test")
@Slf4j
@SpringBootTest(classes = {ShareSightConfig.class})
class StubbedFxTrades {

  @Autowired
  private FxTransactions fxTransactions;

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Autowired
  private ShareSightService shareSightService;

  @Autowired
  private FxRateService fxRateService;

  @Autowired
  private StaticService staticService;

  @Test
  void are_MarketsFound() {
    MarketResponse markets = staticService.getMarkets();
    assertThat(markets).isNotNull();
    assertThat(markets.getData()).isNotEmpty();
  }

  @Test
  void are_CurrenciesFound() {
    CurrencyResponse currencies = staticService.getCurrencies();
    assertThat(currencies).isNotNull();
    assertThat(currencies.getData()).isNotEmpty();
  }


  @Test
  void is_FxContractHonoured() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("EUR").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("GBP").build());
    currencyPairs.add(CurrencyPair.builder().from("USD").to("NZD").build());

    String testDate = "2019-11-12";
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(currencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    assertThat(fxPairResults.getRates().size()).isEqualTo(currencyPairs.size());

    for (CurrencyPair currencyPair : currencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(currencyPair);
      assertThat(fxPairResults.getRates().get(currencyPair))
          .hasFieldOrPropertyWithValue("date", testDate);
    }
  }

  @Test
  void is_EarlyDateWorking() {
    Collection<CurrencyPair> currencyPairs = new ArrayList<>();
    currencyPairs.add(CurrencyPair.builder().from("USD").to("SGD").build());
    currencyPairs.add(CurrencyPair.builder().from("GBP").to("NZD").build());

    String testDate = "1996-07-27"; // Earlier than when ECB started recording rates
    FxResponse fxResponse = fxRateService.getRates(FxRequest.builder()
        .rateDate(testDate)
        .pairs(currencyPairs)
        .build());
    assertThat(fxResponse).isNotNull().hasNoNullFieldsOrProperties();
    FxPairResults fxPairResults = fxResponse.getData();
    for (CurrencyPair currencyPair : currencyPairs) {
      assertThat(fxPairResults.getRates()).containsKeys(currencyPair);
      assertThat(fxPairResults.getRates().get(currencyPair))
          .hasFieldOrPropertyWithValue("date", "1999-01-04");
    }
  }

  @Test
  void is_FxRatesSetFromCurrencies() throws Exception {

    List<String> row = new ArrayList<>();

    String testDate = "27/07/2019"; // Sharesight format

    // NZD Portfolio
    // USD System Base
    // GBP Trade

    row.add(ShareSightTrades.market, "LSE");
    row.add(ShareSightTrades.code, "BHP");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, testDate);
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "GBP");
    row.add(ShareSightTrades.fxRate, null);
    row.add(ShareSightTrades.value, "2097.85");

    Transformer trades = shareSightTransformers.transformer(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("TEST", getCurrency("NZD"));

    Trn trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRateOverridenFromSourceData() throws Exception {

    List<String> row = new ArrayList<>();
    // NZD Portfolio
    // USD System Base
    // GBP Trade
    assertThat(shareSightService.isRatesIgnored()).isTrue();

    row.add(ShareSightTrades.market, "LSE");
    row.add(ShareSightTrades.code, "BHP");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "27/07/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "GBP");
    // With switch true, ignore the supplied rate and pull from service
    row.add(ShareSightTrades.fxRate, "99.99");
    row.add(ShareSightTrades.value, "2097.85");

    Transformer trades = shareSightTransformers.transformer(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));

    Trn trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRatesSetToTransaction() throws Exception {

    List<String> row = new ArrayList<>();

    // Trade CCY USD
    row.add(ShareSightTrades.market, "NASDAQ");
    row.add(ShareSightTrades.code, "MSFT");
    row.add(ShareSightTrades.name, "MSFT");
    row.add(ShareSightTrades.type, "BUY");
    row.add(ShareSightTrades.date, "18/10/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "100");
    row.add(ShareSightTrades.brokerage, null);
    row.add(ShareSightTrades.currency, "USD");
    row.add(ShareSightTrades.fxRate, null);
    row.add(ShareSightTrades.value, "1000.00");

    Transformer trades = shareSightTransformers.transformer(row);

    // Testing all currency buckets
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));
    portfolio.setBase(getCurrency("GBP"));

    Trn trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("tradeAmount",
            new BigDecimal("1000"))
        .hasFieldOrPropertyWithValue("tradeBaseRate",
            new BigDecimal("1.28929253"))
        .hasFieldOrPropertyWithValue("tradeCashRate",
            new BigDecimal("0.63723696"))
        .hasFieldOrPropertyWithValue("tradePortfolioRate",
            new BigDecimal("0.63723696"))
    ;
  }

  @Test
  void is_RateOfOneSetForUndefinedCurrencies() throws ParseException {

    List<String> row = new ArrayList<>();

    String testDate = "27/07/2019";

    // Trade CCY USD
    row.add(ShareSightTrades.market, "NASDAQ");
    row.add(ShareSightTrades.code, "MSFT");
    row.add(ShareSightTrades.name, "MSFT");
    row.add(ShareSightTrades.type, "BUY");
    row.add(ShareSightTrades.date, testDate);
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "100");
    row.add(ShareSightTrades.brokerage, null);
    row.add(ShareSightTrades.currency, "USD");
    row.add(ShareSightTrades.fxRate, null);
    row.add(ShareSightTrades.value, "1000.00");

    Transformer trades = shareSightTransformers.transformer(row);

    // Testing all currency buckets
    Portfolio portfolio = Portfolio.builder().code("TEST").build();

    Trn trn = trades.from(row, portfolio);
    trn.setCashCurrency(null);

    trn = fxTransactions.applyRates(portfolio, trn);
    // No currencies are defined so rate defaults to 1
    assertThat(trn)
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("tradeBaseRate", FxRate.ONE.getRate())
        .hasFieldOrPropertyWithValue("tradeCashRate", FxRate.ONE.getRate())
        .hasFieldOrPropertyWithValue("tradePortfolioRate", FxRate.ONE.getRate());

  }

  private void assertTransaction(Portfolio portfolio, Trn trn) {
    assertThat(trn)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("GBP"))
        .hasFieldOrPropertyWithValue("cashCurrency", portfolio.getCurrency())
        .hasFieldOrPropertyWithValue("tradeBaseRate", new BigDecimal("0.80474951"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.53457983"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.53457983"))
        .hasFieldOrProperty("tradeDate")
    ;
  }

}



