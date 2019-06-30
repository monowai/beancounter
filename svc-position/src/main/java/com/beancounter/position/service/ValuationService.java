package com.beancounter.position.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.position.integration.MdIntegration;
import com.beancounter.position.model.MarketValue;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Integrations with MarketData providers to obtain prices
 * and value Positions.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Service
public class ValuationService implements Valuation {

  private MdIntegration mdIntegration;

  @Autowired
  ValuationService(MdIntegration mdIntegration) {
    this.mdIntegration = mdIntegration;
  }

  @Override
  public MarketData getPrice(String assetId) {
    return mdIntegration.getMarketData(assetId);
  }

  @Override
  public Positions value(Positions positions) {
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(position.getAsset());
      }
    }

    // Anything to value?
    if (assets.isEmpty()) {
      return positions;
    }

    Collection<MarketData> marketDataResults = mdIntegration.getMarketData(assets);
    for (MarketData marketData : marketDataResults) {
      Position position = positions.get(marketData.getAsset());
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        MarketValue marketValue = MarketValue.builder()
            .price(marketData.getClose())
            .asAt(marketData.getDate())
            .marketValue(marketData.getClose().multiply(position.getQuantityValues().getTotal()))
            .build();

        position.addMarketValue(Position.In.LOCAL, marketValue);
        postProcess(position, marketValue);
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }

  private void postProcess(Position position, MarketValue marketValue) {
    MoneyValues localMoney = position.getMoneyValue(Position.In.LOCAL);
    localMoney.setUnrealisedGain(marketValue.getMarketValue()
        .subtract(position.getMoneyValue(Position.In.LOCAL).getCostValue()));

    localMoney.setTotalGain(localMoney.getUnrealisedGain()
        .add(localMoney.getDividends()
        .add(localMoney.getRealisedGain())));
  }


}
