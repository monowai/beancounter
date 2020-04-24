package com.beancounter.shell.integ;

import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.ingest.FxTransactions;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.CurrencyUtils;
import java.math.BigDecimal;
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
@SpringBootTest(classes = {ShareSightConfig.class, ClientConfig.class})
class StubbedTradesWithFx {

  @Autowired
  private FxTransactions fxTransactions;

  @Autowired
  private ShareSightFactory shareSightFactory;

  @Autowired
  private ShareSightConfig shareSightConfig;

  private final CurrencyUtils currencyUtils = new CurrencyUtils();

  @Test
  void is_FxRatesSetFromCurrencies() {

    List<String> row = new ArrayList<>();

    String testDate = "27/07/2019"; // Sharesight format

    // NZD Portfolio
    // USD System Base
    // GBP Trade

    row.add(ShareSightTradeAdapter.id, "999");
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
    Portfolio portfolio = getPortfolio("TEST", currencyUtils.getCurrency("NZD"));

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .build();

    TrnInput trn = trades.from(trustedTrnRequest);

    fxTransactions.setTrnRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRateOverridenFromSourceData() {

    List<String> row = new ArrayList<>();
    // NZD Portfolio
    // USD System Base
    // GBP Trade
    assertThat(shareSightConfig.isCalculateRates()).isTrue();

    row.add(ShareSightTradeAdapter.id, "999");
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
    Portfolio portfolio = getPortfolio("Test", currencyUtils.getCurrency("NZD"));

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .build();

    TrnInput trn = trades.from(trustedTrnRequest);


    fxTransactions.setTrnRates(portfolio, trn);

    assertTransaction(portfolio, trn);

  }

  @Test
  void is_FxRatesSetAndTradeAmountCalculated() {

    List<String> row = new ArrayList<>();

    // Trade CCY USD
    row.add(ShareSightTradeAdapter.id, "333");
    row.add(ShareSightTradeAdapter.market, "NASDAQ");
    row.add(ShareSightTradeAdapter.code, "MSFT");
    row.add(ShareSightTradeAdapter.name, "MSFT");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "18/10/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "100");
    row.add(ShareSightTradeAdapter.brokerage, null);
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, null); // This rate will be ignored
    row.add(ShareSightTradeAdapter.value, "1001.00"); // This rate will be ignored

    TrnAdapter trades = shareSightFactory.adapter(row);

    // Testing all currency buckets
    Portfolio portfolio = getPortfolio("Test", currencyUtils.getCurrency("NZD"));
    portfolio.setBase(currencyUtils.getCurrency("GBP"));

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .build();

    TrnInput trn = trades.from(trustedTrnRequest);

    fxTransactions.setTrnRates(portfolio, trn);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("tradeCurrency", "USD")
        // Was tradeAmount calculated?
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("1000.00"))
        .hasFieldOrPropertyWithValue("tradeBaseRate", new BigDecimal("1.28929253"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.63723696"))
        .hasFieldOrPropertyWithValue("tradePortfolioRate", new BigDecimal("0.63723696"));
  }

  @Test
  void is_RateOfOneSetForUndefinedCurrencies() {

    List<String> row = new ArrayList<>();

    String testDate = "27/07/2019";

    // Trade CCY USD
    row.add(ShareSightTradeAdapter.id, "222");
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

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .build();

    TrnInput trn = trades.from(trustedTrnRequest);

    trn.setCashCurrency(null);
    fxTransactions.setTrnRates(portfolio, trn);

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



