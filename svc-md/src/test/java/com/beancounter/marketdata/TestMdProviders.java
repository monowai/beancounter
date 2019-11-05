package com.beancounter.marketdata;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.providers.alpha.AlphaConfig;
import com.beancounter.marketdata.providers.alpha.AlphaService;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.providers.wtd.WtdConfig;
import com.beancounter.marketdata.providers.wtd.WtdService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = {
    AlphaConfig.class,
    WtdConfig.class,
    MdFactory.class,
    MockProviderService.class})
class TestMdProviders {
  private MdFactory mdFactory;

  @Autowired
  TestMdProviders(MdFactory mdFactory) {
    this.mdFactory = mdFactory;
  }

  @Test
  void is_DefaultMarketProvidersSet() {
    assertThat(mdFactory.getMarketDataProvider(WtdService.ID)).isNotNull();
    assertThat(mdFactory.getMarketDataProvider(AlphaService.ID)).isNotNull();
    assertThat(mdFactory.getMarketDataProvider(MockProviderService.ID)).isNotNull();

    MarketDataProvider mdp = mdFactory.getMarketDataProvider(AssetUtils
        .getAsset("ABC", Market.builder().code("NonExistent").build()));

    assertThat(mdp)
        .isNotNull()
        .hasFieldOrPropertyWithValue("ID", MockProviderService.ID);
  }
}
