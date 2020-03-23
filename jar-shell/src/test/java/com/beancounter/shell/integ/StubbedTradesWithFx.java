package com.beancounter.shell.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.FxTransactions;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.shell.ingest.TrnAdapter;
import com.beancounter.shell.sharesight.ShareSightConfig;
import com.beancounter.shell.sharesight.ShareSightFactory;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTradeAdapter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
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
class StubbedTradesWithFx {

  @Autowired
  private FxTransactions fxTransactions;

  @Autowired
  private ShareSightFactory shareSightFactory;

  @Autowired
  private ShareSightService shareSightService;

  @Test
  void is_FxRatesSetFromCurrencies() throws Exception {

    List<Object> row = new ArrayList<>();

    String testDate = "27/07/2019"; // Sharesight format

    // NZD Portfolio
    // USD System Base
    // GBP Trade

    row.add(ShareSightTradeAdapter.market, "LSE");
    row.add(ShareSightTradeAdapter.code, "BHP");
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, "buy");
    row.add(ShareSightTradeAdapter.date, testDate);
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "GBP");
    row.add(ShareSightTradeAdapter.fxRate, null);
    row.add(ShareSightTradeAdapter.value, "2097.85");

    TrnAdapter trades = shareSightFactory.adapter(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("TEST", getCurrency("NZD"));

    TrnInput trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRateOverridenFromSourceData() throws Exception {

    List<Object> row = new ArrayList<>();
    // NZD Portfolio
    // USD System Base
    // GBP Trade
    assertThat(shareSightService.isRatesIgnored()).isTrue();

    row.add(ShareSightTradeAdapter.market, "LSE");
    row.add(ShareSightTradeAdapter.code, "BHP");
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, "buy");
    row.add(ShareSightTradeAdapter.date, "27/07/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "GBP");
    // With switch true, ignore the supplied rate and pull from service
    row.add(ShareSightTradeAdapter.fxRate, "99.99");
    row.add(ShareSightTradeAdapter.value, "2097.85");

    TrnAdapter trades = shareSightFactory.adapter(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));

    TrnInput trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRatesSetToTransaction() throws Exception {

    List<Object> row = new ArrayList<>();

    // Trade CCY USD
    row.add(ShareSightTradeAdapter.market, "NASDAQ");
    row.add(ShareSightTradeAdapter.code, "MSFT");
    row.add(ShareSightTradeAdapter.name, "MSFT");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "18/10/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "100");
    row.add(ShareSightTradeAdapter.brokerage, null);
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, null);
    row.add(ShareSightTradeAdapter.value, "1000.00");

    TrnAdapter trades = shareSightFactory.adapter(row);

    // Testing all currency buckets
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));
    portfolio.setBase(getCurrency("GBP"));

    TrnInput trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
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

    List<Object> row = new ArrayList<>();

    String testDate = "27/07/2019";

    // Trade CCY USD
    row.add(ShareSightTradeAdapter.market, "NASDAQ");
    row.add(ShareSightTradeAdapter.code, "MSFT");
    row.add(ShareSightTradeAdapter.name, "MSFT");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, testDate);
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "100");
    row.add(ShareSightTradeAdapter.brokerage, null);
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, null);
    row.add(ShareSightTradeAdapter.value, "1000.00");

    TrnAdapter trades = shareSightFactory.adapter(row);

    // Testing all currency buckets
    Portfolio portfolio = getPortfolio("TEST", Currency.builder()
        .code("USD").build());

    TrnInput trn = trades.from(row, portfolio);
    trn.setCashCurrency(null);
    trn = fxTransactions.applyRates(portfolio, trn);

    // No currencies are defined so rate defaults to 1
    assertThat(trn)
        .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
        .hasFieldOrPropertyWithValue("tradeBaseRate", FxRate.ONE.getRate())
        .hasFieldOrPropertyWithValue("tradeCashRate", FxRate.ONE.getRate())
        .hasFieldOrPropertyWithValue("tradePortfolioRate", FxRate.ONE.getRate());

  }

  private void assertTransaction(Portfolio portfolio, TrnInput trn) {
    assertThat(trn)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("tradeCurrency", "GBP")
        .hasFieldOrPropertyWithValue("cashCurrency", portfolio.getCurrency().getCode())
        .hasFieldOrPropertyWithValue("tradeBaseRate", new BigDecimal("0.80474951"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.53457983"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.53457983"))
        .hasFieldOrProperty("tradeDate")
    ;
  }

}



