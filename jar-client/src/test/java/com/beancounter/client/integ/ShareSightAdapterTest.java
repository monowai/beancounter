package com.beancounter.client.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.client.sharesight.ShareSightRowAdapter;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.PortfolioUtils;
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
@SpringBootTest(classes = {ShareSightConfig.class, ClientConfig.class})
class ShareSightAdapterTest {

  @Autowired
  private ShareSightFactory shareSightFactory;

  @Autowired
  private ShareSightConfig shareSightConfig;

  @Autowired
  private ShareSightRowAdapter shareSightRowProcessor;

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
    assertThat(shareSightConfig.isCalculateRates()).isTrue();
  }

  private void verifyMarketCode(String code, Asset expectedAsset) {
    List<String> row = new ArrayList<>();
    row.add("1");
    row.add(code);
    Asset asset = shareSightFactory.getShareSightDivi().resolveAsset(row);

    assertThat(asset.getMarket().getCode())
        .isEqualTo(expectedAsset.getMarket().getCode());
  }


  @Test
  void is_AssetsSetIntoTransaction() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.id, "1");
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
    row.add(ShareSightTradeAdapter.id, "2");
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

    Portfolio portfolio = PortfolioUtils.getPortfolio("TEST");
    for (List<String> columnValues : rows) {
      TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
          .row(columnValues)
          .portfolio(portfolio)
          .callerRef(CallerRef.builder().build())
          .build();

      trnInputs.add(shareSightRowProcessor
          .transform(trustedTrnRequest));
    }
    assertThat(trnInputs).hasSize(2);

    for (TrnInput trn : trnInputs) {
      assertThat(trn)
          .hasFieldOrProperty("callerRef")
          .hasFieldOrProperty("asset")
          .hasFieldOrProperty("fees")
          .hasFieldOrProperty("quantity")
          .hasFieldOrProperty("tradeCurrency")
          .hasFieldOrProperty("trnType")
          .hasFieldOrProperty("tradeDate");

    }

  }
}
