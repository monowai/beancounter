package com.beancounter.ingest;

import static com.beancounter.common.helper.AssetHelper.getAsset;
import static com.beancounter.ingest.UnitTestHelper.getCurrency;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.helper.MathHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.ingest.config.ExchangeConfig;
import com.beancounter.ingest.reader.Transformer;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.beancounter.ingest.sharesight.common.ShareSightHelper;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;


/**
 * Sharesight Dividend converter to BC model..
 *
 * @author mikeh
 * @since 2019-02-12
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
        ExchangeConfig.class,
        ShareSightTransformers.class,
        ShareSightDivis.class,
        ShareSightTrades.class,
        ShareSightHelper.class})
class ShareSightDiviTest {


  @Autowired
  private ShareSightTransformers shareSightTransformers;

  private MathHelper mathHelper = new MathHelper();

  @Test
  @VisibleForTesting
  void is_CurrencyResolvedForDividendInput() throws Exception {
    List<String> row = new ArrayList<>();

    // Portfolio is in NZD
    Portfolio portfolio = Portfolio.builder()
        .code("TEST")
        .currency(Currency.builder().code("NZD").build())
        .build();

    // System base currency
    Currency base = Currency.builder().code("USD").build();

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

    Transaction transaction = dividends.from(row, portfolio, base);
    Asset expectedAsset = getAsset("MO", "NYSE");

    BigDecimal fxRate = new BigDecimal(rate);
    assertThat(transaction)
        .hasFieldOrPropertyWithValue("asset", expectedAsset)
        .hasFieldOrPropertyWithValue("tradeCashRate", fxRate)
        .hasFieldOrPropertyWithValue("tradeAmount",
            mathHelper.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("cashAmount",
            mathHelper.multiply(new BigDecimal("15.85"), fxRate))
        .hasFieldOrPropertyWithValue("tax", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("baseCurrency", getCurrency("USD"))
        .hasFieldOrPropertyWithValue("portfolio", portfolio)

        .hasFieldOrProperty("tradeDate")
    ;

  }
}
