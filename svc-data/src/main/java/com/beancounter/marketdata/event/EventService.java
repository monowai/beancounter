package com.beancounter.marketdata.event;

import com.beancounter.common.contracts.EventRequest;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.assets.AssetService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService {
  private final EventRepository eventRepository;
  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  private String topicEvent;
  @Value("${kafka.enabled:true}")
  private Boolean kafkaEnabled;

  private final AssetService assetService;

  private KafkaTemplate<String, EventRequest> kafkaCaProducer;

  public EventService(EventRepository eventRepository, AssetService assetService) {
    this.eventRepository = eventRepository;
    this.assetService = assetService;
  }

  @Autowired
  public void setKafkaCaProducer(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") KafkaTemplate<String, EventRequest> kafkaCaProducer) {
    this.kafkaCaProducer = kafkaCaProducer;
  }

  public CorporateEvent save(CorporateEvent event) {

    Optional<CorporateEvent> existing = eventRepository.findByAssetAndPayDate(
        event.getAsset(),
        event.getPayDate());
    if (existing.isPresent()) {
      return existing.get();
    }

    event.setId(KeyGenUtils.getId());

    CorporateEvent result = eventRepository.save(event);
    // On Save
    result.setAsset(assetService.hydrateAsset(result.getAsset()));
    if (kafkaEnabled) {
      dispatch(result);
    }
    return result;
  }

  void dispatch(CorporateEvent corporateEvent) {
    log.info("Dispatch {} ... {}", topicEvent, corporateEvent);

    kafkaCaProducer.send(
        new ProducerRecord<>(topicEvent, EventRequest.builder().data(corporateEvent).build()));
  }

}
