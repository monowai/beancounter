package com.beancounter.googled.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Transaction;
import com.beancounter.googled.config.ExchangeConfig;
import com.beancounter.googled.sharesight.ShareSightDivis;
import com.beancounter.googled.sharesight.ShareSightHelper;
import com.beancounter.googled.sharesight.ShareSightTrades;
import com.beancounter.googled.sharesight.ShareSightTransformers;
import com.beancounter.googled.sharesight.Transformer;
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
@SpringBootTest(classes = {
    ExchangeConfig.class,
    ShareSightTransformers.class,
    ShareSightDivis.class,
    ShareSightTrades.class,
    ShareSightHelper.class})
class ShareSightDiviTest {

  @Autowired
  private ShareSightHelper shareSightHelper;

  @Autowired
  private ShareSightTransformers shareSightTransformers;


  @Test
  void convertRowToTransaction() throws Exception {

    List<String> row = new ArrayList<>();

    row.add(ShareSightDivis.code, "MO.NYS");
    row.add(ShareSightDivis.name, "Test Asset");
    row.add(ShareSightDivis.date, "21/01/2019");
    row.add(ShareSightDivis.fxRate, "0.8988");
    row.add(ShareSightDivis.currency, "AUD");
    row.add(ShareSightDivis.net, "10.09");
    row.add(ShareSightDivis.tax, "12.23");
    row.add(ShareSightDivis.gross, "12.99");
    row.add(ShareSightDivis.comments, "Test Comment");

    Transformer dividends = shareSightTransformers.getTransformer(row);
    
    Transaction transaction = dividends.of(row);

    Asset expectedAsset = Asset.builder()
        .code("MO")
        .market(Market.builder().code("NYSE").build())
        .build();

    assertThat(transaction)
        .hasFieldOrPropertyWithValue("asset", expectedAsset)
        .hasFieldOrPropertyWithValue("tradeRate", new BigDecimal(".8988"))
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("10.09")
            .multiply(new BigDecimal("0.8988")))
        .hasFieldOrPropertyWithValue("tax", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrProperty("tradeDate")
    ;

  }
}
