package com.beancounter.ingest.integ;

import static com.beancounter.ingest.UnitTestHelper.getCurrency;
import static com.beancounter.ingest.UnitTestHelper.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.service.FxTransactions;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Tag("slow")
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@SpringBootTest
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = "beancounter:svc-md:+:stubs:8090")
@DirtiesContext
@ActiveProfiles("test")
@Slf4j
class TradesWithFx {

  @Autowired
  private FxTransactions fxTransactions;

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  @VisibleForTesting
  void is_FxRatesSetFromCurrencies() throws Exception {

    List<String> row = new ArrayList<>();

    String testDate = "27/07/2019";

    // NZD Portfolio
    // USD System Base
    // GBP Trade

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
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
    Portfolio portfolio = getPortfolio(Currency.builder().code("NZD").build());

    // System base currency
    Currency base = getCurrency("USD");

    Transaction transaction = trades.from(row, portfolio, base);

    transaction = fxTransactions.applyRates(transaction);

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio())
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("GBP"))
        .hasFieldOrPropertyWithValue("baseCurrency", base)
        .hasFieldOrPropertyWithValue("cashCurrency", portfolio.getCurrency())
        .hasFieldOrPropertyWithValue("tradeBaseRate", new BigDecimal("0.66428103"))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.53457983"))
        .hasFieldOrPropertyWithValue("tradeRefRate", new BigDecimal("0.53457983"))
        .hasFieldOrProperty("tradeDate")
    ;

  }


}
