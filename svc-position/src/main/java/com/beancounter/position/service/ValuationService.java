package com.beancounter.position.service;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.position.accumulation.Gains;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.integration.BcGateway;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;


/**
 * Integrations with MarketData providers to obtain prices
 * and value Positions.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Slf4j
@Configuration
@Service
public class ValuationService implements Valuation {

  private BcGateway bcGateway;
  private Gains gains = new Gains();
  private MarketValue marketValue = new MarketValue();

  @Value("${marketdata.url}")
  private String mdUrl;

  @Autowired
  ValuationService(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @Override
  public Positions value(Positions positions) {
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      gains.value(position, Position.In.PORTFOLIO);
      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(position.getAsset());
      }
    }

    return value(positions, assets);
  }

  private Positions value(Positions positions, Collection<Asset> assets) {
    // Anything to value?
    if (assets.isEmpty()) {
      return positions;
    }
    log.debug("Valuing {} positions against : {}", positions.getPositions().size(), mdUrl);

    // Set market data into the positions
    PriceResponse priceResponse = bcGateway.getMarketData(assets);
    for (MarketData marketData : priceResponse.getData()) {
      Position position = positions.get(marketData.getAsset());
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        marketValue.value(position, Position.In.PORTFOLIO, marketData, BigDecimal.ONE);
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }
}
