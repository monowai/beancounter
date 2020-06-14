package com.beancounter.marketdata.event;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.utils.MathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventWriter {
  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  private String topicEvent;
  @Value("${kafka.enabled:true}")
  private final Boolean kafkaEnabled = false;

  private KafkaTemplate<String, TrustedEventInput> kafkaCaProducer;

  @Autowired
  public void setKafkaCaProducer(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                     KafkaTemplate<String, TrustedEventInput> kafkaCaProducer) {
    this.kafkaCaProducer = kafkaCaProducer;
  }

  public void write(CorporateEvent corporateEvent) {
    if (!kafkaEnabled) {
      return;
    }

    if (MathUtils.isUnset(corporateEvent.getRate())) {
      return;
    }

    log.trace("Dispatch {} ... {}", topicEvent, corporateEvent);
    kafkaCaProducer.send(topicEvent, TrustedEventInput.builder()
        .data(corporateEvent)
        .build());
  }


}
