package com.beancounter.position.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.position.integration.BcGateway;
import com.beancounter.position.model.Position;
import com.beancounter.position.model.Positions;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
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

  @Value("${marketdata.url}")
  private String mdUrl;

  @Autowired
  ValuationService(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @Override
  public MarketData getPrice(String assetId) {
    return bcGateway.getMarketData(assetId);
  }

  @Override
  public Positions value(Positions positions) {
    log.debug("Valuing {} positions against : {}",
        positions.getPositions().size(),
        mdUrl);

    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      gains(
          position.getMoneyValue(Position.In.PORTFOLIO),
          position.getQuantityValues().getTotal()
      );
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

    // Set market data into the positions
    Collection<MarketData> marketDataResults = bcGateway.getMarketData(assets);
    for (MarketData marketData : marketDataResults) {
      Position position = positions.get(marketData.getAsset());
      if (!marketData.getClose().equals(BigDecimal.ZERO)) {
        value(
            position.getMoneyValue(Position.In.PORTFOLIO),
            marketData,
            position.getQuantityValues().getTotal());
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }

  private void value(MoneyValues moneyValues, MarketData marketData, BigDecimal total) {
    // Only called if total != Zero
    moneyValues.setPrice(marketData.getClose());
    moneyValues.setAsAt(marketData.getDate());
    moneyValues.setMarketValue(marketData.getClose().multiply(total));

    gains(moneyValues, total);

  }

  private void gains(MoneyValues moneyValues, BigDecimal total) {
    // Gains need to be calculated ever if we don't value the asset due to a zero total
    if (!Objects.equals(total, BigDecimal.ZERO)) {
      moneyValues.setUnrealisedGain(moneyValues.getMarketValue()
          .subtract(moneyValues.getCostValue()));
    }

    moneyValues.setTotalGain(moneyValues.getUnrealisedGain()
        .add(moneyValues.getDividends()
            .add(moneyValues.getRealisedGain())));
  }

}
