package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.helper.AssetHelper;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.util.Dates;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Data provider tests.
 *
 * @author mikeh
 * @since 2019-03-01
 */

class TestMockProviderService {

  @Test
  void mockDataProviderReturnsValue() {
    Asset asset = AssetHelper.getAsset("Anything", "MOCK");
    MarketDataProvider provider = new MockProviderService(new Dates());
    MarketData result = provider.getCurrent(asset);
    assertThat(result)
        .isNotNull()
        .hasFieldOrPropertyWithValue("close", new BigDecimal("999.99"));
  }

  @Test
  void mockDataProviderThrowsException() {
    // Hard coded asset exception
    Asset asset = AssetHelper.getAsset("123", "MOCK");
    MarketDataProvider provider = new MockProviderService(new Dates());
    assertThrows(BusinessException.class, () -> provider.getCurrent(asset));
  }

}
