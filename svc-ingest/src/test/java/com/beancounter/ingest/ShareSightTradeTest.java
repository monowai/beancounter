package com.beancounter.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.config.ShareSightConfig;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
@SpringBootTest(classes = {
    ShareSightConfig.class
    })
@Slf4j
class ShareSightTradeTest {

  @Autowired
  private RowProcessor rowProcessor;

  @Test
  @VisibleForTesting
  void is_RowWithFxConverted() throws Exception {

    List<Object> row = getRow("buy", "0.8988", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    // Portfolio is in NZD
    Portfolio portfolio = UnitTestHelper.getPortfolio();

    // System base currency
    Currency base = UnitTestHelper.getCurrency("USD");
    Collection<Transaction> transactions = rowProcessor.process(portfolio, values, "Test");

    Transaction transaction = transactions.iterator().next();
    log.info(new ObjectMapper().writeValueAsString(transaction));
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("14.45"))
        .hasFieldOrPropertyWithValue("tradeAmount", new MathUtils()
            .multiply(new BigDecimal("2097.85"), new BigDecimal("0.8988")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeCurrency")
        .hasFieldOrPropertyWithValue("portfolio", UnitTestHelper.getPortfolio())
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.8988"))
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  @VisibleForTesting
  void is_RowWithoutFxConverted()  {

    List<Object> row = getRow("buy", "0.0", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    // Portfolio is in NZD
    Portfolio portfolio = UnitTestHelper.getPortfolio();

    Transaction transaction = rowProcessor.process(portfolio, values, "Blah")
        .iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("12.99"))

        .hasFieldOrPropertyWithValue("tradeAmount", new MathUtils()
            .multiply(new BigDecimal("2097.85"), new BigDecimal("0")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", UnitTestHelper.getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolio", UnitTestHelper.getPortfolio())
        .hasFieldOrPropertyWithValue("tradeCashRate", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  @VisibleForTesting
  void is_RowWithNoCommentTransformed() {

    List<Object> row = getRow("buy", "0.8988", "2097.85");
    row.remove(ShareSightTrades.comments);
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    Transaction transaction =
        rowProcessor.process(UnitTestHelper.getPortfolio(), values, "twee")
            .iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  @VisibleForTesting
  void is_SplitTransactionTransformed() {

    List<Object> row = getRow("split", null, null);
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    Transaction transaction = rowProcessor
        .process(UnitTestHelper.getPortfolio(), values, "blah").iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("10"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", UnitTestHelper.getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolio", UnitTestHelper.getPortfolio())

        .hasFieldOrProperty("tradeDate")
    ;

  }

  private List<Object> getRow(String tranType, String fxRate, String tradeAmount) {
    List<Object> row = new ArrayList<>();

    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, tranType);
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, fxRate);
    row.add(ShareSightTrades.value, tradeAmount);
    row.add(ShareSightTrades.comments, "Test Comment");
    return row;
  }

}
