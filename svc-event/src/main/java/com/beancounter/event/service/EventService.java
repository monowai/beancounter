package com.beancounter.event.service;

import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.input.TrustedEventInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.event.contract.CorporateEventResponse;
import com.beancounter.event.contract.CorporateEventsResponse;
import com.beancounter.event.integration.EventPublisher;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventService {

  private final EventRepository eventRepository;
  private final PositionService positionService;
  private EventPublisher eventPublisher;

  public EventService(PositionService positionService,
                      EventRepository eventRepository) {
    this.positionService = positionService;
    this.eventRepository = eventRepository;
  }

  @Autowired(required = false)
  public void setEventPublisher(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }


  public Collection<TrustedTrnEvent> processMessage(TrustedEventInput eventRequest) {
    if (eventRequest.getData() != null) {
      return processMessage(
          save(eventRequest.getData())
      );
    }
    return null;
  }

  public Collection<TrustedTrnEvent> processMessage(CorporateEvent event) {
    Collection<TrustedTrnEvent> results = new ArrayList<>();
    PortfoliosResponse response = positionService.findWhereHeld(
        event.getAssetId(),
        event.getRecordDate());
    if (response != null && response.getData() != null) {
      for (Portfolio portfolio : response.getData()) {
        TrustedTrnEvent trnEvent = positionService.process(portfolio, event);
        // Don't create forward dated transactions
        if (trnEvent != null && trnEvent.getTrnInput() != null) {
          if (eventPublisher != null) {
            eventPublisher.send(trnEvent);
          }
          results.add(trnEvent);
        }

      }
    }
    return results;
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

  public CorporateEventResponse get(String id) {
    Optional<CorporateEvent> result = eventRepository.findById(id);
    return result
        .map(corporateEvent -> CorporateEventResponse.builder().data(corporateEvent).build())
        .orElse(null);
  }

  public CorporateEventsResponse getAssetEvents(String assetId) {
    Collection<CorporateEvent> events = eventRepository.findByAssetId(assetId);
    CorporateEventsResponse response = CorporateEventsResponse.builder().build();

    for (CorporateEvent event : events) {
      response.getData().add(event);
    }
    return response;

  }

  public Collection<CorporateEvent> forAsset(String assetId) {
    return eventRepository.findByAssetId(assetId);
  }

  public void backFillEvents(String portfolioCode, String valuationDate) {
    positionService.backFillEvents(portfolioCode, valuationDate);
  }

  public Collection<CorporateEvent> findInRange(LocalDate start, LocalDate end) {
    return eventRepository.findByDateRange(start, end);
  }
}