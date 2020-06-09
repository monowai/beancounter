package com.beancounter.event.service;

import org.springframework.stereotype.Service;

@Service
public class EventService {

  private final EventBehaviourFactory behaviourFactory ;

  public EventService(EventBehaviourFactory eventBehaviourFactory) {
    this.behaviourFactory = eventBehaviourFactory;
  }
}
