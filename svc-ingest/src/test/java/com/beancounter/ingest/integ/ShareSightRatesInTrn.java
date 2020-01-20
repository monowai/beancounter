package com.beancounter.ingest.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static com.beancounter.ingest.integ.ShareSightTradeTest.getRow;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.config.ShareSightConfig;
import com.beancounter.ingest.reader.RowProcessor;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightService;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@ActiveProfiles("test-nobackfill")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@SpringBootTest(classes = {ShareSightConfig.class})

public class ShareSightRatesInTrn {

  @Autowired
  private ShareSightService shareSightService;

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Autowired
  private RowProcessor rowProcessor;

  @Test
  void is_IgnoreRatesDefaultCorrect() {
    assertThat(shareSightService.isRatesIgnored()).isFalse();
  }

  @Test
  void is_DividendRowWithFxConverted() throws Exception {
    List<String> row = new ArrayList<>();

    // Portfolio is in NZD
    Portfolio portfolio = Portfolio.builder()
        .code("TEST")
        .currency(Currency.builder().code("NZD").build())
        .build();

    // Trade is in USD
    row.add(ShareSightDivis.code, "ABBV.NYS");
    row.add(ShareSightDivis.name, "Test Asset");
    row.add(ShareSightDivis.date, "21/01/2019");
    String rate = "0.8074"; // Sharesight Trade to Reference Rate
    row.add(ShareSightDivis.fxRate, rate);
    row.add(ShareSightDivis.currency, "USD"); // TradeCurrency
    row.add(ShareSightDivis.net, "15.85");
    row.add(ShareSightDivis.tax, "0");
    row.add(ShareSightDivis.gross, "15.85");
    row.add(ShareSightDivis.comments, "Test Comment");

    Transformer dividends = shareSightTransformers.transformer(row);

    Trn trn = dividends.from(row, portfolio);
    Asset expectedAsset = AssetUtils.getAsset("ABBV", "NYSE");

    BigDecimal fxRate = new BigDecimal(rate);
    assertThat(trn)
        .hasFieldOrPropertyWithValue("asset.code", expectedAsset.getCode())
        .hasFieldOrPropertyWithValue("tradeCashRate", fxRate)
        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("cashAmount",
            MathUtils.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("tax", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("portfolioId", portfolio.getId())

        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_TradeRowWithFxConverted() throws Exception {

    List<Object> row = getRow("buy", "0.8988", "2097.85");
    List<List<Object>> values = new ArrayList<>();
    values.add(row);
    // Portfolio is in NZD
    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));

    // System base currency
    Collection<Trn> trns = rowProcessor.transform(portfolio, values, "Test");

    Trn trn = trns.iterator().next();
    log.info(new ObjectMapper().writeValueAsString(trn));
    assertThat(trn)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("14.45"))
        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("2097.85"), new BigDecimal("0.8988")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeCurrency")
        .hasFieldOrPropertyWithValue("portfolioId", portfolio.getId())
        .hasFieldOrPropertyWithValue("tradeCashRate", new BigDecimal("0.8988"))
        .hasFieldOrProperty("tradeDate")
    ;

  }


}
