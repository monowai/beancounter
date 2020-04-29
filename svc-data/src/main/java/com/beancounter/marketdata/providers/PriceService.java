package com.beancounter.marketdata.providers;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.KeyGenUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

@Service
public class PriceService {

  private final MarketDataRepo marketDataRepo;

  PriceService(MarketDataRepo marketDataRepo) {
    this.marketDataRepo = marketDataRepo;
  }

  public Optional<MarketData> getMarketData(String assetId, LocalDate date) {
    return marketDataRepo.findByAssetIdAndPriceDate(assetId, date);

  }

  public Iterable<MarketData> process(PriceResponse priceResponse) {
    Collection<MarketData> createSet = new ArrayList<>();
    for (MarketData marketData : priceResponse.getData()) {
      if (marketData.getAsset().isKnown()) {
        Optional<MarketData> existing = marketDataRepo.findByAssetIdAndPriceDate(
            marketData.getAsset().getId(),
            marketData.getPriceDate());

        if (existing.isEmpty()) {
          // Create
          marketData.setId(KeyGenUtils.getId());
          createSet.add(marketData);
        }
      }
    }
    if (createSet.isEmpty()) {
      return new ArrayList<>();
    }
    return marketDataRepo.saveAll(createSet);
  }

  @Async
  public Future<Iterable<MarketData>> write(PriceResponse priceResponse) {
    Iterable<MarketData> results = process(priceResponse);
    return new AsyncResult<>(results);
  }
}
