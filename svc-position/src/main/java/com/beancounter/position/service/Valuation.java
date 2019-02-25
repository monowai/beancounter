package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;
import com.beancounter.position.model.Positions;

/**
 * Valuation services are responsible for computing
 * the market value of Positions.
 * 
 * @author mikeh
 * @since 2019-02-24
 */
public interface Valuation {

  // Dev only feature
  MarketData getPrice(String assetId);

  Positions value(Positions positions);


}
