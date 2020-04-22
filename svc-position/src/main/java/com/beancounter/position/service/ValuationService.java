package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.valuation.Gains;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;


/**
 * Values requested positions against market prices.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@Slf4j
@Configuration
@Service
public class ValuationService implements Valuation {

  private final Gains gains;
  private final PositionValuationService positionValuationService;
  private DateUtils dateUtils = new DateUtils();

  @Autowired
  ValuationService(PositionValuationService positionValuationService, Gains gains) {
    this.positionValuationService = positionValuationService;
    this.gains = gains;
  }

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  @Override
  public PositionResponse value(Positions positions) {

    if (positions == null) {
      return PositionResponse.builder().build();
    }
    if (positions.getAsAt() != null) {
      dateUtils.isValid(positions.getAsAt());
    }
    Collection<AssetInput> assets = new ArrayList<>();
    for (Position position : positions.getPositions().values()) {
      gains.value(position.getQuantityValues().getTotal(),
          position.getMoneyValues(Position.In.PORTFOLIO));

      if (!position.getQuantityValues().getTotal().equals(BigDecimal.ZERO)) {
        assets.add(AssetInput.builder()
            .code(position.getAsset().getCode())
            .market(position.getAsset().getMarket().getCode())
            .build());
      }
    }
    Positions valuedPositions = positionValuationService.value(positions, assets);
    return PositionResponse.builder()
        .data(valuedPositions)
        .build();
  }


}
