package com.beancounter.marketdata.providers.mock;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.service.MarketDataProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.stereotype.Service;

/**
 * For testing purposes. Part of the main source in order to allow for an off-line provider
 * that will force certain error conditions.
 *
 * @author mikeh
 * @since 2019-03-01
 */

@Service
public class MockProviderService implements MarketDataProvider {
  public static final String ID = "MOCK";

  public MarketData getPrices(Asset asset) {
    if (asset.getCode().equalsIgnoreCase("123")) {
      throw new BusinessException(
          String.format("Invalid asset code [%s]", asset.getCode()));
    }
    return MarketData.builder()
        .asset(asset)
        .close(BigDecimal.valueOf(999.99))
        .open(BigDecimal.valueOf(999.99))
        .date(getPriceDate())
        .build();
  }

  @Override
  public Collection<MarketData> getPrices(PriceRequest priceRequest) {
    Collection<MarketData> results = new ArrayList<>(priceRequest.getAssets().size());
    for (Asset asset : priceRequest.getAssets()) {
      results.add(getPrices(asset));
    }
    return results;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isMarketSupported(Market market) {
    return "MOCK".equalsIgnoreCase(market.getCode());

  }

  public String getPriceDate() {
    return "2019-11-21";
  }


}
