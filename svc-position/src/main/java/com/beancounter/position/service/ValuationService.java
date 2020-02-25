package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.Gains;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  private PositionValuationService positionValuationService;

  @Autowired
  ValuationService(PositionValuationService positionValuationService) {
    this.positionValuationService = positionValuationService;
  }

  @Override
  public PositionResponse value(Positions positions) {

    if (positions == null) {
      return PositionResponse.builder()
          .data(null)
          .build();
    }
    if (positions.getAsAt() != null) {
      DateUtils.isValid(positions.getAsAt());
    }
    Collection<Asset> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      Gains.value(position, Position.In.PORTFOLIO);
      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(position.getAsset());
      }
    }

    return PositionResponse.builder()
        .data(positionValuationService.value(positions, assets))
        .build();
  }


}
