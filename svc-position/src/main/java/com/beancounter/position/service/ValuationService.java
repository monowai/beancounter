package com.beancounter.position.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.position.integration.MdIntegration;
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

  private MdIntegration mdIntegration;

  @Value("${beancounter.md.url}")
  private String mdUrl;

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
    log.debug("Valuing positions against svc-md api: {}", mdUrl);
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      gains(position);
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
        position.getMoneyValue(Position.In.TRADE)
            .setPrice(marketData.getClose());
        position.getMoneyValue(Position.In.TRADE)
            .setAsAt(marketData.getDate());
        position.getMoneyValue(Position.In.TRADE)
            .setMarketValue(marketData.getClose()
                .multiply(position.getQuantityValues().getTotal()));

        gains(position);
        position.setAsset(marketData.getAsset());
      }
    }
    return positions;
  }

  private void gains(Position position) {
    MoneyValues localMoney = position.getMoneyValue(Position.In.TRADE);

    if (!Objects.equals(position.getQuantityValues().getTotal(), BigDecimal.ZERO)) {
      localMoney.setUnrealisedGain(position.getMoneyValue(Position.In.TRADE).getMarketValue()
          .subtract(position.getMoneyValue(Position.In.TRADE).getCostValue()));
    }

    localMoney.setTotalGain(localMoney.getUnrealisedGain()
        .add(localMoney.getDividends()
            .add(localMoney.getRealisedGain())));
  }


}
