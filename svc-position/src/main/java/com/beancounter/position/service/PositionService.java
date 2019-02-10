package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;
import com.beancounter.position.integration.MdIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Returns collections of positions for a Portfolio.
 * @author mikeh
 * @since 2019-02-01
 */
@Service
public class PositionService implements Position {

  private MdIntegration mdIntegration;

  @Autowired
  PositionService(MdIntegration mdIntegration) {
    this.mdIntegration = mdIntegration;
  }

  @Override
  public MarketData getPrice(String assetId) {
    return mdIntegration.getMarketData(assetId);
  }
}
