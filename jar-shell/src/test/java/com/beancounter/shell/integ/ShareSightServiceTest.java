package com.beancounter.shell.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.PortfolioUtils;
import com.beancounter.shell.config.ShareSightConfig;
import com.beancounter.shell.reader.RowProcessor;
import com.beancounter.shell.sharesight.ShareSightDivis;
import com.beancounter.shell.sharesight.ShareSightService;
import com.beancounter.shell.sharesight.ShareSightTrades;
import java.math.BigDecimal;
import java.text.ParseException;
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
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = "org.beancounter:svc-data:+:stubs:10999")
@SpringBootTest(classes = {ShareSightConfig.class})
class ShareSightServiceTest {

  @Autowired
  private ShareSightService shareSightService;

  @Autowired
  private RowProcessor rowProcessor;

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

    verifyMarketCode("ABBV.NYSE", expectedAsset);

    expectedAsset = Asset.builder()
        .code("AMP")
        .market(Market.builder().code("ASX").build())
        .build();

    verifyMarketCode("AMP.AX", expectedAsset);
  }

  @Test
  void is_IgnoreRatesCorrect() {
    assertThat(shareSightService.isRatesIgnored()).isTrue();
  }

  private void verifyMarketCode(String code, Asset expectedAsset) {

    Asset asset = shareSightService.resolveAsset(code);

    assertThat(asset.getMarket().getCode())
        .isEqualTo(expectedAsset.getMarket().getCode());
  }

  @Test
  void is_ValidTradeRow() {
    List<String> row = new ArrayList<>();
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
    List<String> row = new ArrayList<>();
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

  @Test
  void is_AssetsSetIntoTransaction()  {
    List<Object> row = new ArrayList<>();
    row.add(ShareSightTrades.market, "ASX");
    row.add(ShareSightTrades.code, "BHP");
    row.add(ShareSightTrades.name, "Test Asset");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "AUD");
    row.add(ShareSightTrades.fxRate, "99.99");
    row.add(ShareSightTrades.value, "2097.85");

    List<List<Object>> rows = new ArrayList<>();
    rows.add(row);

    row = new ArrayList<>();
    row.add(ShareSightTrades.market, "NASDAQ");
    row.add(ShareSightTrades.code, "MSFT");
    row.add(ShareSightTrades.name, "Microsoft");
    row.add(ShareSightTrades.type, "buy");
    row.add(ShareSightTrades.date, "21/01/2019");
    row.add(ShareSightTrades.quantity, "10");
    row.add(ShareSightTrades.price, "12.23");
    row.add(ShareSightTrades.brokerage, "12.99");
    row.add(ShareSightTrades.currency, "USD");
    row.add(ShareSightTrades.fxRate, "99.99");
    row.add(ShareSightTrades.value, "2097.85");

    rows.add(row);

    assertThat(rows).hasSize(2);

    Collection<Trn> trns = rowProcessor
        .transform(PortfolioUtils.getPortfolio("TEST"), rows, "Test");

    for (Trn trn : trns) {
      assertThat(trn.getAsset().getId()).isNotNull();
    }

  }
}
