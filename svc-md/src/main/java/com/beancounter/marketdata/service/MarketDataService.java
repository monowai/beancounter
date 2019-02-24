package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.stereotype.Service;

/**
 * Service container for MarketData information.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Service
public class MarketDataService implements MarketDataProvider {
  @Override
  public MarketData getCurrent(Asset asset) {
    if (asset.getCode().equalsIgnoreCase("123")) {
      throw new BusinessException(
          String.format("Invalid asset code [%s]", asset.getCode()));
    }

    return MarketData.builder()
        .asset(asset)
        .close(BigDecimal.valueOf(999.99))
        .open(BigDecimal.valueOf(999.99))
        .build();
  }

  @Override
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    Collection<MarketData> results = new ArrayList<>(assets.size());
    for (Asset asset : assets) {
      results.add(getCurrent(asset));
    }
    return results;
  }
}
