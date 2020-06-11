package com.beancounter.marketdata;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = {MarketDataBoot.class})
@ActiveProfiles("test")
class TestMdProviders {
  private final MdFactory mdFactory;
  private final MarketService marketService;

  @Autowired
  TestMdProviders(MdFactory mdFactory, MarketService marketService) {
    this.mdFactory = mdFactory;
    this.marketService = marketService;
  }

  @Test
  void is_DefaultMarketProvidersSet() {
    assertThat(mdFactory.getMarketDataProvider(WtdService.ID)).isNotNull();
    assertThat(mdFactory.getMarketDataProvider(AlphaService.ID)).isNotNull();
    assertThat(mdFactory.getMarketDataProvider(MockProviderService.ID)).isNotNull();

    MarketDataProvider mdp = mdFactory.getMarketDataProvider(
        Market.builder().code("NonExistent").build());

    assertThat(mdp)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ID", MockProviderService.ID);
  }

  @Test
  void is_FoundByMarket() {
    Asset amp = AssetUtils.getAsset(marketService.getMarket("ASX"), "AMP");
    MarketDataProvider asxMarket = mdFactory.getMarketDataProvider(amp.getMarket());
    assertThat(asxMarket.getId()).isEqualTo(AlphaService.ID);

    Asset gne = AssetUtils.getAsset(marketService.getMarket("NZX"), "GNE");
    MarketDataProvider nzxMarket = mdFactory.getMarketDataProvider(gne.getMarket());
    assertThat(nzxMarket.getId()).isEqualTo(WtdService.ID);

    assertThat(nzxMarket.isMarketSupported(gne.getMarket())).isTrue();
    assertThat(nzxMarket.isMarketSupported(amp.getMarket())).isFalse();

  }

  @Test
  void is_InvalidMarketException() {
    assertThrows(BusinessException.class, () -> marketService.getMarket("illegal"));
  }
}
