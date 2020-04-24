package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightDividendAdapter;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.PortfolioUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = {ShareSightConfig.class})
public class TestAdapters {

  @Autowired
  private ShareSightConfig shareSightConfig;

  @MockBean
  private AssetIngestService assetIngestService;

  @MockBean
  FxService fxService;

  @Test
  void is_DividendIllegalNumber() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightDividendAdapter.id, "1");
    row.add(ShareSightDividendAdapter.code, "market"); // Header Row
    row.add(ShareSightDividendAdapter.name, "name");
    row.add(ShareSightDividendAdapter.date, "date");
    row.add(ShareSightDividendAdapter.fxRate, "A.B");

    TrustedTrnRequest request = TrustedTrnRequest.builder()
        .row(row)
        .build();

    TrnAdapter dividendAdapter =
        new ShareSightDividendAdapter(shareSightConfig, assetIngestService);

    assertThrows(BusinessException.class, () -> dividendAdapter.from(request));

  }

  @Test
  void is_NullTrnTypeCorrect() {

    List<String> row = new ArrayList<>();
    row.add("");//ID
    row.add("");//Dode
    row.add("");
    row.add(null);
    row.add("");

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .build();

    ShareSightTradeAdapter tradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService);

    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(trustedTrnRequest));

  }

  @Test
  void is_BlankTrnTypeCorrect() {

    List<String> row = new ArrayList<>();
    row.add("");
    row.add("");
    row.add("");
    row.add("");
    row.add("");
    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .build();

    ShareSightTradeAdapter tradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService);

    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(trustedTrnRequest));

  }

  @Test
  void is_ValidTradeRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.id, "1");
    row.add(ShareSightTradeAdapter.market, "market"); // Header Row
    row.add(ShareSightTradeAdapter.code, "code");
    row.add(ShareSightTradeAdapter.name, "name");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "date");
    row.add(ShareSightTradeAdapter.quantity, "quantity");
    row.add(ShareSightTradeAdapter.price, "price");
    ShareSightTradeAdapter shareSightTradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService);
    assertThat(shareSightTradeAdapter.isValid(row)).isTrue();

  }

  @Test
  void is_TradeAmountComputed() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.id, "1");
    row.add(ShareSightTradeAdapter.market, "NYSE"); // Header Row
    row.add(ShareSightTradeAdapter.code, "ABC");
    row.add(ShareSightTradeAdapter.name, "name");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "23/11/2018");
    row.add(ShareSightTradeAdapter.quantity, "10");
    row.add(ShareSightTradeAdapter.price, "10.0");
    row.add(ShareSightTradeAdapter.brokerage, "5.0");
    row.add(ShareSightTradeAdapter.currency, "USD");
    row.add(ShareSightTradeAdapter.fxRate, null);
    row.add(ShareSightTradeAdapter.value, null);
    row.add(ShareSightTradeAdapter.comments, null);

    ShareSightTradeAdapter shareSightTradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService);
    when(assetIngestService.resolveAsset("NYSE", "ABC", "name"))
        .thenReturn(AssetUtils.getAsset("NYSE", "ABC"));
    TrnInput result = shareSightTradeAdapter.from(TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .build());

    assertThat(result)
        .hasFieldOrPropertyWithValue("tradeAmount", new BigDecimal("105.00"))
    ;

  }

  @Test
  void is_ValidDividendRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.id, "1");
    row.add(ShareSightDividendAdapter.code, "code"); // Header Row
    row.add(ShareSightDividendAdapter.name, "code");
    row.add(ShareSightDividendAdapter.date, "name");
    row.add(ShareSightDividendAdapter.fxRate, "1.0");
    row.add(ShareSightDividendAdapter.currency, "date");
    row.add(ShareSightDividendAdapter.net, "quantity");
    row.add(ShareSightDividendAdapter.tax, "tax");
    row.add(ShareSightDividendAdapter.gross, "gross");
    row.add(ShareSightDividendAdapter.comments, "comments");
    ShareSightDividendAdapter dividendAdapter =
        new ShareSightDividendAdapter(shareSightConfig, assetIngestService);
    assertThat(dividendAdapter.isValid(row)).isTrue();

  }
}
