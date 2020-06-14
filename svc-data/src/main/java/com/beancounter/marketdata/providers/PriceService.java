package com.beancounter.marketdata.providers;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.event.EventWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PriceService {

  private final MarketDataRepo marketDataRepo;
  private EventWriter eventWriter;

  PriceService(MarketDataRepo marketDataRepo) {
    this.marketDataRepo = marketDataRepo;
  }

  @Autowired
  public void setEventWriter(EventWriter eventWriter) {
    this.eventWriter = eventWriter;
  }

  public Optional<MarketData> getMarketData(String assetId, LocalDate date) {
    return marketDataRepo.findByAssetIdAndPriceDate(assetId, date);

  }

  @Async
  public Future<Iterable<MarketData>> write(PriceResponse priceResponse) {
    return new AsyncResult<>(process(priceResponse));
  }

  public Iterable<MarketData> process(PriceResponse priceResponse) {
    if (priceResponse.getData() == null) {
      return null;
    }
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
        eventWriter.write(CorporateEvent.builder()
            .assetId(marketData.getAsset().getId())
            .trnType(TrnType.DIVI)
            .source(marketData.getSource())
            .recordDate(marketData.getPriceDate())
            .rate(marketData.getDividend())
            .split(marketData.getSplit())
            .build());
      }
    }
    if (createSet.isEmpty()) {
      return createSet;
    }
    return marketDataRepo.saveAll(createSet);
  }

  public void purge() {
    marketDataRepo.deleteAll();
  }
}
