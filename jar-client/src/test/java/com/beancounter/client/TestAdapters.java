package com.beancounter.client;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.client.services.AssetService;
import com.beancounter.client.services.StaticService;
import com.beancounter.client.sharesight.ShareSightConfig;
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

  @MockBean
  private ShareSightConfig shareSightConfig;

  @MockBean
  private AssetService assetService;

  @MockBean
  private StaticService staticService;

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
        new ShareSightTradeAdapter(shareSightConfig, assetService, staticService);

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
        new ShareSightTradeAdapter(shareSightConfig, assetService, staticService);

    assertThrows(BusinessException.class, ()
        -> tradeAdapter.from(trustedTrnRequest));

  }

}
