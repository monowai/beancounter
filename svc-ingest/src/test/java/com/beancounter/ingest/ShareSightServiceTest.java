package com.beancounter.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.ingest.config.ShareSightConfig;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightService;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(classes = {ShareSightConfig.class})
class ShareSightServiceTest {

  @Autowired
  private ShareSightService shareSightService;

  @Test
  void is_DoubleValueInputCorrect() throws ParseException {
    assertThat(shareSightService.parseDouble("5,000.99"))
        .isEqualByComparingTo(BigDecimal.valueOf(5000.99));
  }

  @Test
  void is_ExceptionThrownResolvingIncorrectAssetCodes() {
    assertThrows(BusinessException.class, ()
        -> shareSightService.resolveAsset(null));
    assertThrows(BusinessException.class, ()
        -> shareSightService.resolveAsset("ValueWithNoSeparator"));
  }

  @Test
  void is_ExchangeAliasReturnedInAssetCode() {
    Asset expectedAsset = Asset.builder()
        .code("ABBV")
        .market(Market.builder().code("NYSE").build())
        .build();

    verifyAsset("ABBV.NYSE", expectedAsset);

    expectedAsset = Asset.builder()
        .code("AMP")
        .market(Market.builder().code("ASX").build())
        .build();

    verifyAsset("AMP.AX", expectedAsset);
  }

  @Test
  void is_IgnoreRatesDefaultCorrect() {
    assertThat(shareSightService.isRatesIgnored()).isFalse();
  }

  private void verifyAsset(String code, Asset expectedAsset) {

    Asset asset = shareSightService.resolveAsset(code);

    assertThat(asset)
        .isEqualToComparingFieldByField(expectedAsset);
  }

  @Test
  void is_ValidTradeRow() {
    List<Object> row = new ArrayList<>();
    row.add(ShareSightTrades.market, "market"); // Header Row
    row.add(ShareSightTrades.code, "code");
    row.add(ShareSightTrades.name, "name");
    row.add(ShareSightTrades.type, "type");
    row.add(ShareSightTrades.date, "date");
    row.add(ShareSightTrades.quantity, "quantity");
    row.add(ShareSightTrades.price, "price");
    ShareSightTrades shareSightTrades = new ShareSightTrades(shareSightService);
    assertThat(shareSightTrades.isValid(row)).isFalse();

    row.remove(ShareSightTrades.price);
    assertThat(shareSightTrades.isValid(row)).isFalse(); // Not enough columns for a trade
  }

  @Test
  void is_ValidDividendRow() {
    List<Object> row = new ArrayList<>();
    row.add(ShareSightDivis.code, "code"); // Header Row
    row.add(ShareSightDivis.name, "code");
    row.add(ShareSightDivis.date, "name");
    row.add(ShareSightDivis.fxRate, "type");
    row.add(ShareSightDivis.currency, "date");
    row.add(ShareSightDivis.net, "quantity");
    row.add(ShareSightDivis.tax, "tax");
    row.add(ShareSightDivis.gross, "gross");
    row.add(ShareSightDivis.comments, "comments");
    ShareSightDivis shareSightDivis = new ShareSightDivis(shareSightService);
    assertThat(shareSightDivis.isValid(row)).isFalse();// Ignore CODE

    row.remove(ShareSightTrades.code);
    assertThat(shareSightDivis.isValid(row)).isFalse(); // Not enough columns for a trade

    row.add(ShareSightDivis.code, "total");
    assertThat(shareSightDivis.isValid(row)).isFalse(); // Ignore TOTAL
    row.remove(ShareSightTrades.code);
    row.add(ShareSightDivis.code, "some.code");
    assertThat(shareSightDivis.isValid(row)).isTrue();
  }
}
