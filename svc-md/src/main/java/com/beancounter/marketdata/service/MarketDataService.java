package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service container for MarketData information.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Service
public class MarketDataService {

  private MdFactory mdFactory;

  @Autowired
  MarketDataService(MdFactory mdFactory) {
    this.mdFactory = mdFactory;
  }

  public MarketData getCurrent(Asset asset) {
    return mdFactory.getMarketDataProvider(asset).getCurrent(asset);
  }

  /**
   * MarketData for a Collection of assets.
   * @param assets to query
   * @return results
   */
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    Collection<MarketData> results = new ArrayList<>(assets.size());
    for (Asset asset : assets) {
      results.add(getCurrent(asset));
    }
    return results;
  }
}
