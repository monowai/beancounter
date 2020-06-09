package com.beancounter.event.service;

import com.beancounter.common.contracts.EventRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EventConsumer {

  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  public String topicEvent;

  private final EventService eventService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public EventConsumer(EventService eventService) {
    this.eventService = eventService;
  }

  @Bean
  public NewTopic topicEvent() {
    return new NewTopic(topicEvent, 1, (short) 1);
  }

  @Bean
  public String caTopic() {
    log.info("Topic: CA-EVENT set to {}", topicEvent);
    return topicEvent;
  }

  @KafkaListener(topics = "#{@caTopic}", errorHandler = "bcErrorHandler")
  public void processMessage(String message) throws JsonProcessingException {
    EventRequest eventRequest = objectMapper.readValue(message, EventRequest.class);
    if (eventRequest.getData() != null) {
      log.info("CA {}", eventRequest.getData().getAsset().getCode());
      eventService.process(eventRequest.getData());
    }
  }

}
