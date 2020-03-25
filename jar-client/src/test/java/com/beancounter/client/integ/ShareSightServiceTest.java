package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightDividendAdapater;
import com.beancounter.client.sharesight.ShareSightRowAdapter;
import com.beancounter.client.sharesight.ShareSightService;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.PortfolioUtils;
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
  private ShareSightRowAdapter shareSightRowProcessor;

  @Test
  void is_NullDateSupported() {
    assertThat(shareSightService.parseDate(null)).isNull();
  }

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
    row.add(ShareSightTradeAdapter.market, "market"); // Header Row
    row.add(ShareSightTradeAdapter.code, "code");
    row.add(ShareSightTradeAdapter.name, "name");
    row.add(ShareSightTradeAdapter.type, "type");
    row.add(ShareSightTradeAdapter.date, "date");
    row.add(ShareSightTradeAdapter.quantity, "quantity");
    row.add(ShareSightTradeAdapter.price, "price");
    ShareSightTradeAdapter shareSightTradeAdapter = new ShareSightTradeAdapter(shareSightService);
    assertThat(shareSightTradeAdapter.isValid(row)).isFalse();

    row.remove(ShareSightTradeAdapter.price);
    assertThat(shareSightTradeAdapter.isValid(row)).isFalse(); // Not enough columns for a trade
  }

  @Test
  void is_ValidDividendRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightDividendAdapater.code, "code"); // Header Row
    row.add(ShareSightDividendAdapater.name, "code");
    row.add(ShareSightDividendAdapater.date, "name");
    row.add(ShareSightDividendAdapater.fxRate, "type");
    row.add(ShareSightDividendAdapater.currency, "date");
    row.add(ShareSightDividendAdapater.net, "quantity");
    row.add(ShareSightDividendAdapater.tax, "tax");
    row.add(ShareSightDividendAdapater.gross, "gross");
    row.add(ShareSightDividendAdapater.comments, "comments");
    ShareSightDividendAdapater dividendAdapater = new ShareSightDividendAdapater(shareSightService);
    assertThat(dividendAdapater.isValid(row)).isFalse();// Ignore CODE

    row.remove(ShareSightTradeAdapter.code);
    assertThat(dividendAdapater.isValid(row)).isFalse(); // Not enough columns for a trade

    row.add(ShareSightDividendAdapater.code, "total");
    assertThat(dividendAdapater.isValid(row)).isFalse(); // Ignore TOTAL
    row.remove(ShareSightTradeAdapter.code);
    row.add(ShareSightDividendAdapater.code, "some.code");
    assertThat(dividendAdapater.isValid(row)).isTrue();
  }

  @Test
  void is_AssetsSetIntoTransaction() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.market, "ASX");
    row.add(ShareSightTradeAdapter.code, "BHP");
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, "buy");
    row.add(ShareSightTradeAdapter.date, "21/01/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "AUD");
    row.add(ShareSightTradeAdapter.fxRate, "99.99");
    row.add(ShareSightTradeAdapter.value, "2097.85");

    List<List<String>> rows = new ArrayList<>();
    rows.add(row);

    row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.market, "NASDAQ");
    row.add(ShareSightTradeAdapter.code, "MSFT");
    row.add(ShareSightTradeAdapter.name, "Microsoft");
    row.add(ShareSightTradeAdapter.type, "buy");
    row.add(ShareSightTradeAdapter.date, "21/01/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, "99.99");
    row.add(ShareSightTradeAdapter.value, "2097.85");

    rows.add(row);

    Collection<TrnInput> trnInputs = new ArrayList<>();
    Asset asset = AssetUtils.getAsset("MSFT", "NASDAQ");
    for (List<String> strings : rows) {
      trnInputs.add(shareSightRowProcessor
          .transform(PortfolioUtils.getPortfolio("TEST"), asset, strings, "Test"));
    }
    assertThat(trnInputs).hasSize(2);

    for (TrnInput trn : trnInputs) {
      assertThat(trn)
          .hasFieldOrProperty("asset")
          .hasFieldOrProperty("fees")
          .hasFieldOrProperty("quantity")
          .hasFieldOrProperty("tradeCurrency")
          .hasFieldOrProperty("trnType")
          .hasFieldOrProperty("tradeDate");

    }

  }
}
