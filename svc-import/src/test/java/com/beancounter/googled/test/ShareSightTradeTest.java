package com.beancounter.googled.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.googled.config.ExchangeConfig;
import com.beancounter.googled.reader.Transformer;
import com.beancounter.googled.sharesight.ShareSightDivis;
import com.beancounter.googled.sharesight.ShareSightTrades;
import com.beancounter.googled.sharesight.ShareSightTransformers;
import com.beancounter.googled.sharesight.common.ShareSightHelper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
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
class ShareSightTradeTest {

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  void convertRowToTransaction() throws Exception {

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

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount",
            new BigDecimal("2097.85").multiply(new BigDecimal("0.8988"))
                .setScale(2, RoundingMode.HALF_UP))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
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
  void rowWithNoComment() throws Exception {

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
  void splitTransaction() throws Exception {
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

    Transformer transformer = shareSightTransformers.transformer("TRADE");
    assertThat(transformer.isValid(row)).isTrue();

    Transaction transaction = transformer.from(row, getPortfolio(), getCurrency("USD"));

    assertThat(transaction).isNotNull();
  }

  @Test
  void convertToSplit() throws Exception {

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
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount",
            new BigDecimal("0.00"))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", Currency.builder().code("AUD").build())
        .hasFieldOrPropertyWithValue("baseCurrency", Currency.builder().code("USD").build())
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio())

        .hasFieldOrProperty("tradeDate")
    ;

  }

}
