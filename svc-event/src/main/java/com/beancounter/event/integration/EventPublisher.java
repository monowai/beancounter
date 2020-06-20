package com.beancounter.event.integration;

import com.beancounter.common.input.TrustedTrnEvent;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Service
@Slf4j
public class EventPublisher {
  @Value("${beancounter.topics.trn.event:bc-trn-event-dev}")
  public String topicTrnEvent;
  private KafkaTemplate<String, TrustedTrnEvent> kafkaTemplate;

  @PostConstruct
  void logConfig() {
    log.info("TRN-EVENT: {} ", topicTrnEvent);
  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  public void setKafkaTemplate(KafkaTemplate<String, TrustedTrnEvent> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void send(TrustedTrnEvent trnEvent) {
    kafkaTemplate.send(topicTrnEvent, trnEvent);
  }
}
