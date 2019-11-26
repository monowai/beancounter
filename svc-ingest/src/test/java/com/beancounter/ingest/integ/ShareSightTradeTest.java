package com.beancounter.ingest.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.config.ShareSightConfig;
import com.beancounter.ingest.reader.Filter;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;
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
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-md:+:stubs:8090")
@ActiveProfiles("test")
@Slf4j
class ShareSightTradeTest {

  @Autowired
  private RowProcessor rowProcessor;

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  void is_RowWithFxConverted() throws Exception {

    List<Object> row = getRow("buy", "0.8988", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));

    // System base currency
    Collection<Transaction> transactions = rowProcessor.process(portfolio, values, "Test");

    Transaction transaction = transactions.iterator().next();
    log.info(new ObjectMapper().writeValueAsString(transaction));
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("14.45"))
        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("2097.85"), new BigDecimal("0.8988")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeCurrency")
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio("Test", getCurrency("NZD")))
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.8988"))
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_SplitTransformerFoundForRow() {
    List<Object> row = new ArrayList<>();
    row.add(ShareSightTrades.market, "AMEX");
    row.add(ShareSightTrades.code, "SLB");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "split");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");

    Transformer transformer = shareSightTransformers.transformer(row);
    assertThat(transformer).isInstanceOf(ShareSightTrades.class);
  }

  @Test
  void is_RowWithoutFxConverted() {

    List<Object> row = getRow("buy", "0.0", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));

    Transaction transaction = rowProcessor.process(portfolio, values, "Blah")
        .iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("12.99"))

        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("2097.85"), new BigDecimal("0")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolio", getPortfolio("Test", getCurrency("NZD")))
        .hasFieldOrPropertyWithValue("tradeCashRate", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_RowWithNoCommentTransformed() {

    List<Object> row = getRow("buy", "0.8988", "2097.85");
    row.remove(ShareSightTrades.comments);
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    Transaction transaction =
        rowProcessor.process(getPortfolio("Test", getCurrency("NZD")), values, "twee")
            .iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate");

  }

  @Test
  void is_RowFilterWorking() {

    List<Object> inFilter = getRow("buy", "0.8988", "2097.85");
    List<Object> outFilter = getRow("ABC", "ABC", "buy", "0.8988", "2097.85");
    inFilter.remove(ShareSightTrades.comments);
    List<List<Object>> values = new ArrayList<>();
    values.add(inFilter);
    values.add(outFilter);

    Collection<Transaction> transactions =
        rowProcessor.process(
            getPortfolio("Test", getCurrency("NZD")),
            values,
            new Filter("SLB"),
            "twee");

    assertThat(transactions).hasSize(1);
    Transaction transaction = transactions.iterator().next();
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_SplitTransactionTransformed() {

    List<Object> row = getRow("split", null, null);
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));
    Transaction transaction = rowProcessor
        .process(portfolio, values, "blah").iterator().next();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("10"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolio", portfolio)

        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_IllegalNumberFound() {
    List<Object> row = getRow("buy", "0.8988e", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    assertThrows(BusinessException.class, () ->
        rowProcessor.process(getPortfolio("Test", getCurrency("NZD")),
            values, "twee"));
  }

  @Test
  void is_IllegalDateFound() {
    List<Object> row = getRow("buy", "0.8988", "2097.85");
    row.add(ShareSightTrades.date, "21/01/2019'");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    assertThrows(BusinessException.class, () ->
        rowProcessor.process(getPortfolio("Test", getCurrency("NZD")),
            values, "twee"));


  }

  private List<Object> getRow(String tranType, String fxRate, String tradeAmount) {
    return getRow("AMEX", "SLB", tranType, fxRate, tradeAmount);
  }

  private List<Object> getRow(String market,
                              String code,
                              String tranType,
                              String fxRate,
                              String tradeAmount) {
    List<Object> row = new ArrayList<>();

    row.add(ShareSightTrades.market, market);
    row.add(ShareSightTrades.code, code);
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
