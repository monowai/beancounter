package com.beancounter.googled.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.googled.config.ExchangeConfig;
import com.beancounter.googled.format.ShareSightHelper;
import com.beancounter.googled.format.ShareSightTrades;
import com.beancounter.googled.format.Transformer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Sharesight Transaction to BC model.Transaction.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {ExchangeConfig.class, ShareSightTrades.class, ShareSightHelper.class})
@ActiveProfiles("trade")
class ShareSightTradeTest {

  @Autowired
  private Transformer trades;

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
    row.add(ShareSightTrades.fxrate, "0.8988");
    row.add(ShareSightTrades.value, "2097.85");
    row.add(ShareSightTrades.comments, "Test Comment");

    Transaction transaction = trades.of(row);

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantities", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeDate")
    ;

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
    row.add(ShareSightTrades.fxrate, "0.8988");
    row.add(ShareSightTrades.value, "2097.85");

    Transaction transaction = trades.of(row);

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantities", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }
}
