package com.beancounter.marketdata.providers;

import com.beancounter.common.model.MarketData;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface MarketDataRepo extends CrudRepository<MarketData, String> {
  Optional<MarketData> findByAssetIdAndPriceDate(String assetId, LocalDate date);
}
