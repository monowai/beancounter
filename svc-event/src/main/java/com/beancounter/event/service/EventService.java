package com.beancounter.event.service;

import com.beancounter.common.input.TrustedEventInput;
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
public class EventService {

  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  public String topicEvent;

  private final PositionHandler positionHandler;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public EventService(PositionHandler positionHandler) {
    this.positionHandler = positionHandler;
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
    TrustedEventInput eventRequest = objectMapper.readValue(message, TrustedEventInput.class);
    if (eventRequest.getEvent() != null) {
      log.info("CA {}", eventRequest.getEvent().getAsset().getCode());
      positionHandler.process(eventRequest);

    }
  }

}
