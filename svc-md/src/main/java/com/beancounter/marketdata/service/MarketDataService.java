package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Service container for MarketData information.
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
        .assetId(asset.getCode())
        .close(BigDecimal.valueOf(999.99))
        .open(BigDecimal.valueOf(999.99))
        .build();
  }
}
