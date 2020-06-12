package com.beancounter.marketdata.providers;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.event.EventWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PriceService {

  private final MarketDataRepo marketDataRepo;
  private final EventWriter eventWriter;

  PriceService(MarketDataRepo marketDataRepo, EventWriter eventWriter) {
    this.marketDataRepo = marketDataRepo;
    this.eventWriter = eventWriter;
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
        pushCorporateEvent(marketData);
      }
    }
    if (createSet.isEmpty()) {
      return createSet;
    }
    return marketDataRepo.saveAll(createSet);
  }

  private void pushCorporateEvent(MarketData marketData) {
    if (marketData.getDividend() != null
        && marketData.getDividend().compareTo(BigDecimal.ZERO) != 0) {

      eventWriter.write(CorporateEvent.builder()
          .asset(marketData.getAsset())
          .trnType(TrnType.DIVI)
          .source(marketData.getSource())
          .recordDate(marketData.getPriceDate())
          .rate(marketData.getDividend())
          .split(marketData.getSplit())
          .build());
    }
  }

  @Async
  public void write(PriceResponse priceResponse) {
    process(priceResponse);
  }

  public void purge() {
    marketDataRepo.deleteAll();
  }
}
