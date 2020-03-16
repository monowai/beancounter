package com.beancounter.shell.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.reader.Transformer;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTrades;
import com.beancounter.shell.sharesight.ShareSightTransformers;
import com.beancounter.shell.writer.FxTransactions;
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
  private ShareSightTransformers shareSightTransformers;

  @Autowired
  private ShareSightService shareSightService;

  @Test
  void is_FxRatesSetFromCurrencies() throws Exception {

    List<Object> row = new ArrayList<>();

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

    TrnInput trn = trades.from(row, portfolio);

    trn = fxTransactions.applyRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRatesSetToTransaction() throws Exception {

    List<Object> row = new ArrayList<>();

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
    Portfolio portfolio = PortfolioUtils.getPortfolio("TEST", Currency.builder()
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



