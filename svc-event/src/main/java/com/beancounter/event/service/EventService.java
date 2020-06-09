package com.beancounter.event.service;

import com.beancounter.common.model.CorporateEvent;
import org.springframework.stereotype.Service;

@Service
public class EventService {

  private final EventBehaviourFactory behaviourFactory;

  public EventService(EventBehaviourFactory eventBehaviourFactory) {
    this.behaviourFactory = eventBehaviourFactory;
  }

  public void process(CorporateEvent event) {
    // Find where held

    behaviourFactory.getAdapter(event);
  }
}
