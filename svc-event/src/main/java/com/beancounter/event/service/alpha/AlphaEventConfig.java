package com.beancounter.event.service.alpha;

import com.beancounter.event.service.EventBehaviourFactory;
import com.beancounter.event.service.EventService;
import com.beancounter.event.service.TaxService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({TaxService.class, AlphaAdapter.class, EventService.class, EventBehaviourFactory.class})
public class AlphaEventConfig {
}
