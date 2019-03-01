package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.helper.AssetHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.alpha.AlphaProviderService;
import com.beancounter.marketdata.providers.alpha.AlphaRequest;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MarketDataService;
import com.beancounter.marketdata.service.MdFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Data provider tests.
 *
 * @author mikeh
 * @since 2019-03-01
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
    MdFactory.class,
    CloudConfig.class,
    AlphaProviderService.class,
    AlphaRequest.class,
    MarketDataService.class,
    MockProviderService.class})
@ImportAutoConfiguration({FeignAutoConfiguration.class})
class TestMockProviderService {
  @Autowired
  private MdFactory mdFactory;

  @Test
  void mockDataProviderReturnsValue() {
    Asset asset = AssetHelper.getAsset("Anything", "MOCK");
    MarketDataProvider provider = mdFactory.getMarketDataProvider(asset);
    MarketData result = provider.getCurrent(asset);
    assertThat(result)
        .isNotNull()
        .hasFieldOrPropertyWithValue("close", new BigDecimal("999.99"));
  }

  @Test
  void mockDataProviderThrowsException() {
    // Hard coded asset exception
    Asset asset = AssetHelper.getAsset("123", "MOCK");
    MarketDataProvider provider = mdFactory.getMarketDataProvider(asset);
    assertThrows(BusinessException.class, () -> provider.getCurrent(asset));
  }

}
