package com.beancounter.marketdata.event;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CorporateEvent;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface EventRepository extends CrudRepository<CorporateEvent, String> {
  Optional<CorporateEvent> findByAssetAndPayDate(Asset asset, LocalDate exDate);
}
