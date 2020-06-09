package com.beancounter.event.service;

import com.beancounter.common.model.CorporateEvent;
import com.beancounter.event.service.alpha.AlphaAdapter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventBehaviourFactory {
  Map<String, Event> adapters = new HashMap<>();

  EventBehaviourFactory() {
    adapters.put("ALPHA", new AlphaAdapter(new TaxService()));
  }

  public Event getAdapter(CorporateEvent event) {
    return adapters.get(event.getSource());
  }
}
