package com.beancounter.marketdata.event;

import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.portfolio.PortfolioService;
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

  private PortfolioService portfolioService;

  private KafkaTemplate<String, TrustedEventInput> kafkaCaProducer;

  public EventWriter(EventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  @Autowired
  public void setPortfolioService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  @Autowired
  public void setTrnService(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
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
    PortfoliosResponse portfolios = portfolioService
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
