package com.beancounter.ingest.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;


/**
 * Sharesight Dividend converter to BC model..
 *
 * @author mikeh
 * @since 2019-02-12
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-md:+:stubs:10999")
@ActiveProfiles("test")
@Slf4j
@SpringBootTest
class ShareSightDiviTest {

  @Autowired
  private ShareSightTransformers shareSightTransformers;

  @Test
  @VisibleForTesting
  void is_CurrencyResolvedForDividendInput() throws Exception {
    List<String> row = new ArrayList<>();

    // Portfolio is in NZD
    Portfolio portfolio = Portfolio.builder()
        .code("TEST")
        .currency(Currency.builder().code("NZD").build())
        .build();

    // Trade is in USD
    row.add(ShareSightDivis.code, "MO.NYS");
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

    Transaction transaction = dividends.from(row, portfolio);
    Asset expectedAsset = AssetUtils.getAsset("MO", "NYSE");

    BigDecimal fxRate = new BigDecimal(rate);
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("asset.code", expectedAsset.getCode())
        .hasFieldOrPropertyWithValue("tradeCashRate", fxRate)
        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("cashAmount",
            MathUtils.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("tax", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("portfolio", portfolio)

        .hasFieldOrProperty("tradeDate")
    ;

  }
}
