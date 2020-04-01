package com.beancounter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.ingest.AssetIngestService;
import com.beancounter.client.ingest.TrnAdapter;
import com.beancounter.client.services.StaticService;
import com.beancounter.client.sharesight.ShareSightConfig;
import com.beancounter.client.sharesight.ShareSightDividendAdapter;
import com.beancounter.client.sharesight.ShareSightTradeAdapter;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.TrustedTrnRequest;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.common.utils.PortfolioUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

public class TestAdapters {

  private ShareSightConfig shareSightConfig = new ShareSightConfig();

  @MockBean
  private AssetIngestService assetIngestService;

  @MockBean
  private StaticService staticService;

  @Test
  void is_DividendIllegalNumber() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightDividendAdapter.code, "market"); // Header Row
    row.add(ShareSightDividendAdapter.name, "name");
    row.add(ShareSightDividendAdapter.date, "date");
    row.add(ShareSightDividendAdapter.fxRate, "A.B");

    TrustedTrnRequest request = TrustedTrnRequest.builder()
        .row(row)
        .build();

    TrnAdapter dividendAdapter =
        new ShareSightDividendAdapter(shareSightConfig, assetIngestService, staticService);

    assertThrows(BusinessException.class, () -> dividendAdapter.from(request));

  }

  @Test
  void is_NullTrnTypeCorrect() {

    List<String> row = new ArrayList<>();
    row.add("");
    row.add("");
    row.add(null);
    row.add("");

    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .asset(AssetUtils.getAsset("MSFT", "NASDAQ"))
        .build();

    ShareSightTradeAdapter tradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService, staticService);

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
    TrustedTrnRequest trustedTrnRequest = TrustedTrnRequest.builder()
        .row(row)
        .portfolio(PortfolioUtils.getPortfolio("TEST"))
        .asset(AssetUtils.getAsset("MSFT", "NASDAQ"))
        .build();

    ShareSightTradeAdapter tradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService, staticService);

    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(trustedTrnRequest));

  }

  @Test
  void is_ValidTradeRow() {
    List<String> row = new ArrayList<>();
    row.add(ShareSightTradeAdapter.market, "market"); // Header Row
    row.add(ShareSightTradeAdapter.code, "code");
    row.add(ShareSightTradeAdapter.name, "name");
    row.add(ShareSightTradeAdapter.type, "BUY");
    row.add(ShareSightTradeAdapter.date, "date");
    row.add(ShareSightTradeAdapter.quantity, "quantity");
    row.add(ShareSightTradeAdapter.price, "price");
    ShareSightTradeAdapter shareSightTradeAdapter =
        new ShareSightTradeAdapter(shareSightConfig, assetIngestService, staticService);
    assertThat(shareSightTradeAdapter.isValid(row)).isTrue();

  }

  @Test
  void is_ValidDividendRow() {
    List<String> row = new ArrayList<>();
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
        new ShareSightDividendAdapter(shareSightConfig, assetIngestService, staticService);
    assertThat(dividendAdapter.isValid(row)).isTrue();

  }
}
