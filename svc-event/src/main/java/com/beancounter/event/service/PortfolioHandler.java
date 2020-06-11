package com.beancounter.event.service;

import com.beancounter.common.input.TrustedEventInput;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PortfolioHandler {
  private final EventBehaviourFactory behaviourFactory;

  public PortfolioHandler(EventBehaviourFactory eventBehaviourFactory) {
    this.behaviourFactory = eventBehaviourFactory;
  }

  @Async
  public void process(TrustedEventInput eventInput) {
    behaviourFactory.getAdapter(eventInput.getEvent());
  }
}
