package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;
import com.beancounter.position.model.Positions;

/**
 * Valuation services are responsible for return market data information and
 * valuing Positions.
 * 
 * @author mikeh
 * @since 2019-02-24
 */
public interface Valuation {

  MarketData getPrice(String assetId);

  Positions value(Positions positions);


}
