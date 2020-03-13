package com.beancounter.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.service.MarketDataProvider;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Data provider tests.
 *
 * @author mikeh
 * @since 2019-03-01
 */

class TestMockProviderService {

  @Test
  void is_MockProviderReturningValues() {
    Asset asset = AssetUtils.getAsset("Anything", "MOCK");
    MarketDataProvider provider = new MockProviderService();

    Collection<MarketData> result = provider.getMarketData(PriceRequest.of(asset).build());
    assertThat(result)
        .isNotNull()
        .isNotEmpty();
    MarketData marketData = result.iterator().next();
    assertThat(marketData)
        .hasFieldOrPropertyWithValue("close", new BigDecimal("999.99"));
  }

  @Test
  void is_MockDataProviderThrowing() {
    // Hard coded asset exception
    Asset asset = AssetUtils.getAsset("123", "MOCK");
    MarketDataProvider provider = new MockProviderService();
    PriceRequest priceRequest = PriceRequest.builder().build();
    priceRequest.setAssets(Collections.singleton(asset));
    assertThrows(BusinessException.class, () -> provider.getMarketData(priceRequest));
  }

}
