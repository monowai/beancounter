package com.beancounter.position.service;

import com.beancounter.client.services.TrnService;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.valuation.Gains;
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
  private final DateUtils dateUtils = new DateUtils();
  private final TrnService trnService;
  private final PositionService positionService;

  @Autowired
  ValuationService(PositionValuationService positionValuationService,
                   TrnService trnService,
                   PositionService positionService,
                   Gains gains) {
    this.positionValuationService = positionValuationService;
    this.gains = gains;
    this.trnService = trnService;
    this.positionService = positionService;
  }

  @Override
  public PositionResponse build(TrustedTrnQuery trnQuery) {
    TrnResponse trnResponse = trnService.read(trnQuery);
    return buildPositions(trnQuery.getPortfolio(),
        dateUtils.getDateString(trnQuery.getTradeDate()), trnResponse);
  }

  public PositionResponse build(Portfolio portfolio, String valuationDate) {
    TrnResponse trnResponse = trnService.read(portfolio);
    return buildPositions(portfolio, valuationDate, trnResponse);
  }

  private PositionResponse buildPositions(
      Portfolio portfolio,
      String valuationDate,
      TrnResponse trnResponse) {
    PositionRequest positionRequest = PositionRequest.builder()
        .portfolioId(portfolio.getId())
        .trns(trnResponse.getData())
        .build();
    PositionResponse positionResponse = positionService.build(portfolio, positionRequest);
    if (valuationDate != null && !valuationDate.equalsIgnoreCase("today")) {
      positionResponse.getData().setAsAt(valuationDate);
    }
    return positionResponse;
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
    if (positions.hasPositions()) {
      for (Position position : positions.getPositions().values()) {
        gains.value(position.getQuantityValues().getTotal(),
            position.getMoneyValues(Position.In.PORTFOLIO));
        // There's an issue here that without a price, gains are not computed. Still
        // looks better having the current price in the front end anyway.
        //if (!(position.getQuantityValues().getTotal().compareTo(BigDecimal.ZERO) == 0)) {
        assets.add(AssetInput.builder()
            .code(position.getAsset().getCode())
            .market(position.getAsset().getMarket().getCode())
            .build());
        //}
      }
      Positions valuedPositions = positionValuationService.value(positions, assets);
      return PositionResponse.builder()
          .data(valuedPositions)
          .build();
    }
    return PositionResponse.builder()
        .data(positions)
        .build();

  }

}
