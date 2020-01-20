package com.beancounter.ingest.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.config.ShareSightConfig;
import com.beancounter.ingest.reader.Filter;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sharesight Transaction to BC model.Transaction.
 *
 * @author mikeh
 * @since 2019-02-12
 */
@Slf4j
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@SpringBootTest(classes = {ShareSightConfig.class})
class ShareSightTradeTest {

  @Autowired
  private RowProcessor rowProcessor;

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  void is_SplitTransformerFoundForRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTrades.market, "ASX");
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

    Trn trn = rowProcessor.transform(portfolio, values, "Blah")
        .iterator().next();

    assertThat(trn)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("12.99"))

        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("2097.85"), new BigDecimal("0")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolioId", portfolio.getId())
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

    Trn trn =
        rowProcessor.transform(getPortfolio("Test", getCurrency("NZD")), values, "twee")
            .iterator().next();

    assertThat(trn)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate");

  }

  @Test
  void is_RowFilterWorking() {

    List<Object> inFilter = getRow("buy", "0.8988", "2097.85");
    List<Object> outFilter = getRow("ABC", "MOCK", "buy", "0.8988", "2097.85");
    inFilter.remove(ShareSightTrades.comments);
    List<List<Object>> values = new ArrayList<>();
    values.add(inFilter);
    values.add(outFilter);

    Collection<Trn> trns =
        rowProcessor.transform(
            getPortfolio("Test", getCurrency("NZD")),
            values,
            new Filter("AMP"),
            "twee");

    assertThat(trns).hasSize(1);
    Trn trn = trns.iterator().next();
    assertThat(trn)
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
    Trn trn = rowProcessor
        .transform(portfolio, values, "blah").iterator().next();

    assertThat(trn)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("10"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("AUD"))
        .hasFieldOrPropertyWithValue("portfolioId", portfolio.getId())

        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_IllegalNumberFound() {
    List<Object> row = getRow("buy", "0.8988e", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    assertThrows(BusinessException.class, () ->
        rowProcessor.transform(getPortfolio("Test", getCurrency("NZD")),
            values, "twee"));
  }

  @Test
  void is_IllegalDateFound() {
    List<Object> row = getRow("buy", "0.8988", "2097.85");
    row.add(ShareSightTrades.date, "21/01/2019'");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);

    assertThrows(BusinessException.class, () ->
        rowProcessor.transform(getPortfolio("Test", getCurrency("NZD")),
            values, "twee"));


  }

  static List<Object> getRow(String tranType, String fxRate, String tradeAmount) {
    return getRow("AMP", "ASX", tranType, fxRate, tradeAmount);
  }

  static List<Object> getRow(String code, String market,
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
