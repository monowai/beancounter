package com.beancounter.event.service;

import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.KeyGenUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "kafka.enabled", matchIfMissing = true)
@Service
@Slf4j
public class EventService {

  private final EventRepository eventRepository;
  private final PositionService positionService;
  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  public String topicCaEvent;
  @Value("${beancounter.topics.trn.event:bc-trn-event-dev}")
  public String topicTrnEvent;
  private KafkaTemplate<String, TrustedTrnEvent> kafkaTemplate;

  public EventService(PositionService positionService,
                      EventRepository eventRepository) {
    this.positionService = positionService;
    this.eventRepository = eventRepository;
  }

  @Bean
  public NewTopic topicEvent() {
    return new NewTopic(topicCaEvent, 1, (short) 1);
  }

  @Bean
  public String caTopic() {
    log.info("Topics: CA-EVENT: {} TRN-EVENT: {}", topicCaEvent, topicTrnEvent);
    return topicCaEvent;
  }

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  public void setKafkaTemplate(KafkaTemplate<String, TrustedTrnEvent> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @KafkaListener(topics = "#{@caTopic}", errorHandler = "bcErrorHandler")
  public Collection<TrustedTrnEvent> processMessage(TrustedEventInput eventRequest) {
    Collection<TrustedTrnEvent> results = new ArrayList<>();
    if (eventRequest.getData() != null) {
      CorporateEvent event = save(eventRequest.getData());

      PortfoliosResponse response = positionService.findWhereHeld(
          event.getAssetId(),
          event.getRecordDate());
      if (response != null && response.getData() != null) {
        for (Portfolio portfolio : response.getData()) {
          TrustedTrnEvent trnEvent = positionService.process(portfolio, event);

          // Don't create forward dated transactions
          if (trnEvent != null && trnEvent.getTrnInput() != null) {
            kafkaTemplate.send(topicTrnEvent, trnEvent);
            results.add(trnEvent);
          }

        }
        return results;
      }
    }
    return null;
  }

  public CorporateEvent save(CorporateEvent event) {

    Optional<CorporateEvent> existing = eventRepository.findByAssetIdAndRecordDate(
        event.getAssetId(),
        event.getRecordDate());
    if (existing.isPresent()) {
      return existing.get();
    }

    event.setId(KeyGenUtils.getId());
    return eventRepository.save(event);
  }

  public Collection<CorporateEvent> get(String assetId) {
    return eventRepository.findByAssetId(assetId);
  }

}