package com.beancounter.client.integ;

import static com.beancounter.common.utils.CurrencyUtils.getCurrency;
import static com.beancounter.common.utils.PortfolioUtils.getPortfolio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.config.ClientConfig;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.services.PortfolioServiceClient;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightFactory;
import com.beancounter.client.sharesight.ShareSightRowAdapter;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.MathUtils;
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
@SpringBootTest(classes = {ShareSightConfig.class, ClientConfig.class})
class ShareSightTradeTest {

  @Autowired
  private ShareSightRowAdapter shareSightRowProcessor;

  @Autowired
  private PortfolioServiceClient portfolioService;

  @Autowired
  private ShareSightFactory shareSightFactory;

  static List<String> getRow(String tranType, String fxRate, String tradeAmount) {
    return getRow("AMP", "ASX", tranType, fxRate, tradeAmount);
  }

  static List<String> getRow(String code, String market,
                             String tranType,
                             String fxRate,
                             String tradeAmount) {
    List<String> row = new ArrayList<>();

    row.add(ShareSightTradeAdapter.market, market);
    row.add(ShareSightTradeAdapter.code, code);
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, tranType);
    row.add(ShareSightTradeAdapter.date, "21/01/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "AUD");
    row.add(ShareSightTradeAdapter.fxRate, fxRate);
    row.add(ShareSightTradeAdapter.value, tradeAmount);
    row.add(ShareSightTradeAdapter.comments, "Test Comment");
    return row;
  }

  @Test
  void is_SplitTransformerFoundForRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.market, "ASX");
    row.add(ShareSightTradeAdapter.code, "SLB");
    row.add(ShareSightTradeAdapter.name, "Test Asset");
    row.add(ShareSightTradeAdapter.type, "split");
    row.add(ShareSightTradeAdapter.date, "21/01/2019");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "12.23");
    row.add(ShareSightTradeAdapter.brokerage, "12.99");
    row.add(ShareSightTradeAdapter.currency, "AUD");

    TrnAdapter trnAdapter = shareSightFactory.adapter(row);
    assertThat(trnAdapter).isInstanceOf(ShareSightTradeAdapter.class);
  }

  @Test
  void is_RowWithoutFxConverted() {

    List<String> row = getRow("buy", "0.0", "2097.85");

    // Portfolio is in NZD
    Portfolio portfolio = portfolioService.getPortfolioByCode("TEST");
    Asset asset = AssetUtils.getAsset("Why", "ME");
    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .asset(asset)
        .provider("Test")
        .build();

    TrnInput trn = shareSightRowProcessor.transform(trustedTrnRequest);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("trnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("fees", new BigDecimal("12.99"))

        .hasFieldOrPropertyWithValue("tradeAmount",
            MathUtils.multiply(new BigDecimal("2097.85"), new BigDecimal("0")))
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", "AUD")
        .hasFieldOrPropertyWithValue("tradeCashRate", null)
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_RowWithNoCommentTransformed() {

    List<String> row = getRow("buy", "0.8988", "2097.85");
    row.remove(ShareSightTradeAdapter.comments);
    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(getPortfolio("Test", getCurrency("NZD")))
        .asset(AssetUtils.getAsset("Why", "ME"))
        .provider("Test")
        .build();

    TrnInput trn = shareSightRowProcessor.transform(trustedTrnRequest);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.BUY)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal(10))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("comments", null)
        .hasFieldOrProperty("tradeDate");

  }

  @Test
  void is_SplitTransactionTransformed() {

    List<String> row = getRow("split", null, null);

    Portfolio portfolio = getPortfolio("Test", getCurrency("NZD"));
    Asset asset = AssetUtils.getAsset("Why", "ME");

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(portfolio)
        .asset(asset)
        .provider("Test")
        .build();

    TrnInput trn = shareSightRowProcessor
        .transform(trustedTrnRequest);

    assertThat(trn)
        .hasFieldOrPropertyWithValue("TrnType", TrnType.SPLIT)
        .hasFieldOrPropertyWithValue("quantity", new BigDecimal("10"))
        .hasFieldOrPropertyWithValue("price", new BigDecimal("12.23"))
        .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal.ZERO)
        .hasFieldOrPropertyWithValue("comments", "Test Comment")
        .hasFieldOrPropertyWithValue("tradeCurrency", "AUD")
        .hasFieldOrProperty("tradeDate")
    ;

  }

  @Test
  void is_IllegalDateFound() {
    List<String> row = getRow("buy", "0.8988", "2097.85");
    row.add(ShareSightTradeAdapter.date, "21/01/2019'");
    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(getPortfolio("Test", getCurrency("NZD")))
        .asset(AssetUtils.getAsset("Why", "ME"))
        .provider("Test")
        .build();

    assertThrows(BusinessException.class, () ->
        shareSightRowProcessor.transform(trustedTrnRequest));
  }

}
