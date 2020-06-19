package com.beancounter.event.integration;

import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.event.service.EventService;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Service
@Slf4j
public class EventReceiver {

  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  public String topicCaEvent;

  private final EventService eventService;

  public EventReceiver(EventService eventService) {
    this.eventService = eventService;
  }

  @Bean
  public NewTopic topicEvent() {
    return new NewTopic(topicCaEvent, 1, (short) 1);
  }

  @Bean
  public String caTopic() {
    log.info("CA-EVENT: {} ", topicCaEvent);
    return topicCaEvent;
  }

  @KafkaListener(topics = "#{@caTopic}", errorHandler = "bcErrorHandler")
  public Collection<TrustedTrnEvent> processMessage(TrustedEventInput eventRequest) {
    return eventService.processMessage(eventRequest);
  }


}
