package com.beancounter.ingest.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.ingest.config.ExchangeConfig;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Sharesight Transaction to BC model.Transaction.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {ExchangeConfig.class,
    ShareSightTrades.class,
    ShareSightTransformers.class,
    ShareSightDivis.class,
    ShareSightHelper.class})
@Slf4j
class ShareSightTradeTest {

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  void is_RowWithFxConverted() throws Exception {

    List<String> row = new ArrayList<>();

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, "0.8988");
    row.add(ShareSightTrades.value, "2097.85");
    row.add(ShareSightTrades.comments, "Test Comment");

    Transformer trades = shareSightTransformers.transformer(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio();

    // System base currency
    Currency base = getCurrency("USD");

    Transaction transaction = trades.from(row, portfolio, base);
    log.info(new ObjectMapper().writeValueAsString(transaction));
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("14.45"))
        .hasFieldOrPropertyWithValue("tradeAmount", new MathHelper()
            .multiply(new BigDecimal("2097.85"), new BigDecimal("0.8988")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeCurrency")
        .hasFieldOrPropertyWithValue("baseCurrency", base)
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio())
        .hasFieldOrPropertyWithValue("baseRate", BigDecimal.ONE)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_RowWithoutFxConverted() throws Exception {

    List<String> row = new ArrayList<>();

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, "0.0");
    row.add(ShareSightTrades.value, "2097.85");
    row.add(ShareSightTrades.comments, "Test Comment");

    Transformer trades = shareSightTransformers.transformer(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio();

    // System base currency
    Currency base = getCurrency("USD");

    Transaction transaction = trades.from(row, portfolio, base);
    log.info(new ObjectMapper().writeValueAsString(transaction));
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("12.99"))

        .hasFieldOrPropertyWithValue("tradeAmount", new MathHelper()
            .multiply(new BigDecimal("2097.85"), new BigDecimal("0")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("baseCurrency", base)
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio())
        .hasFieldOrPropertyWithValue("baseRate", BigDecimal.ONE)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  private Currency getCurrency(String currency) {
    return Currency.builder().code(currency).build();
  }

  private Portfolio getPortfolio() {
    return Portfolio.builder()
        .code("TEST")
        .currency(getCurrency("NZD"))
        .build();
  }

  @Test
  void is_RowWithNoCommentTransformed() throws Exception {

    List<String> row = new ArrayList<>();

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, "0.8988");
    row.add(ShareSightTrades.value, "2097.85");

    Transformer trades = shareSightTransformers.transformer(row);
    Transaction transaction = trades.from(row, getPortfolio(), getCurrency("USD"));

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_SplitTransactionTransformed() throws Exception {

    List<String> row = new ArrayList<>();

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "split");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, null);
    row.add(ShareSightTrades.value, null);
    row.add(ShareSightTrades.comments, "Test Comment");

    Transformer trades = shareSightTransformers.transformer("TRADE");
    Transaction transaction = trades.from(row, getPortfolio(), getCurrency("USD"));

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("10"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("baseCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio())

        .hasFieldOrProperty("tradeDate")
    ;

  }

}
