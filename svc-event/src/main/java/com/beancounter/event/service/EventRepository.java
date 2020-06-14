package com.beancounter.event.service;

import com.beancounter.common.event.CorporateEvent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface EventRepository extends CrudRepository<CorporateEvent, String> {
  Optional<CorporateEvent> findByAssetIdAndRecordDate(String assetId, LocalDate recordDate);

  Collection<CorporateEvent> findByAssetId(String assetId);
}
