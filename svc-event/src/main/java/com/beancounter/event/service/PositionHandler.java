package com.beancounter.event.service;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.input.TrustedTrnQuery;
import com.beancounter.common.model.Position;
import com.beancounter.event.integration.PositionGateway;
import org.springframework.stereotype.Service;

@Service
public class PositionHandler {
  private final EventBehaviourFactory behaviourFactory;
  private final PositionGateway positionGateway;

  public PositionHandler(EventBehaviourFactory eventBehaviourFactory,
                         PositionGateway positionGateway) {
    this.behaviourFactory = eventBehaviourFactory;
    this.positionGateway = positionGateway;
  }

  public TrustedTrnEvent process(TrustedEventInput eventInput) {
    PositionResponse positionResponse = positionGateway.query(TrustedTrnQuery.builder()
        .portfolio(eventInput.getPortfolio())
        .tradeDate(eventInput.getEvent().getRecordDate())
        .assetId(eventInput.getEvent().getAsset().getId())
        .build());
    if (positionResponse.getData() != null && positionResponse.getData().hasPositions()) {
      Position position = positionResponse.getData().get(eventInput.getEvent().getAsset());
      Event behaviour = behaviourFactory.getAdapter(eventInput.getEvent());
      assert (behaviour != null);
      return behaviour
          .generate(positionResponse.getData().getPortfolio(), position, eventInput.getEvent());
    }
    return null;
  }
}
