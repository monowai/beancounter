package com.beancounter.marketdata.event;

import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.trn.TrnService;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventWriter {
  private final EventRepository eventRepository;
  @Value("${beancounter.topics.ca.event:bc-ca-event-dev}")
  private String topicEvent;
  @Value("${kafka.enabled:true}")
  private Boolean kafkaEnabled;

  private final AssetService assetService;
  private TrnService trnService;

  private KafkaTemplate<String, TrustedEventInput> kafkaCaProducer;

  public EventWriter(EventRepository eventRepository, AssetService assetService) {
    this.eventRepository = eventRepository;
    this.assetService = assetService;
  }

  @Autowired
  public void setTrnService(TrnService trnService) {
    this.trnService = trnService;
  }

  @Autowired
  public void setKafkaCaProducer(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                     KafkaTemplate<String, TrustedEventInput> kafkaCaProducer) {
    this.kafkaCaProducer = kafkaCaProducer;
  }

  public CorporateEvent save(CorporateEvent event) {

    Optional<CorporateEvent> existing = eventRepository.findByAssetAndRecordDate(
        event.getAsset(),
        event.getRecordDate());
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

  @Async
  public void write(CorporateEvent corporateEvent) {
    save(corporateEvent);
  }

  void dispatch(CorporateEvent corporateEvent) {
    log.trace("Dispatch {} ... {}", topicEvent, corporateEvent);
    PortfoliosResponse portfolios = trnService
        .findWhereHeld(corporateEvent.getAsset().getId(), corporateEvent.getRecordDate());
    for (Portfolio portfolio : portfolios.getData()) {
      kafkaCaProducer.send(
          new ProducerRecord<>(topicEvent, TrustedEventInput.builder()
              .event(corporateEvent)
              .portfolio(portfolio)
              .build())
      );

    }
  }

  public Collection<CorporateEvent> get(Asset asset) {
    return eventRepository.findByAsset(asset);
  }

  public void purge() {
    eventRepository.deleteAll();
  }
}
