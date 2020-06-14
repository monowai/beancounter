package com.beancounter.event.service;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.event.service.alpha.AlphaEventAdapter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventBehaviourFactory {
  Map<String, Event> adapters = new HashMap<>();

  public EventBehaviourFactory() {
    adapters.put("ALPHA", new AlphaEventAdapter(new TaxService()));
  }

  public Event getAdapter(CorporateEvent event) {
    return adapters.get(event.getSource());
  }
}
