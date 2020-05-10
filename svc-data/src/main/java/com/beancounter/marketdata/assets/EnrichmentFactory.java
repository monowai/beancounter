package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EnrichmentFactory {
  private final Map<String, AssetEnricher> enrichers = new HashMap<>();

  @Value("${beancounter.enricher:ALPHA}")
  private String defEnricher;

  @Autowired
  void setEnrichers(AssetEnricher figiEnricher, AssetEnricher alphaEnricher) {
    enrichers.put("ALPHA", alphaEnricher);
    enrichers.put("FIGI", figiEnricher);
    enrichers.put("MOCK", new MockEnricher());
    log.info("Registered Asset {} Enrichers.  Default {}", enrichers.keySet().size(), defEnricher);
  }

  AssetEnricher getEnricher(Market market) {
    String enricher = market.getEnricher();
    if (enricher == null) {
      enricher = defEnricher;
    }
    return enrichers.get(enricher.toUpperCase());
  }

}
