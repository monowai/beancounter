package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.markets.MarketService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FigiProxy {

  public static final String FIGI = "FIGI";
  private FigiGateway figiGateway;
  private final MarketService marketService;
  private final FigiConfig figiConfig;
  private FigiAdapter figiAdapter;
  private final Collection<String> filter = new ArrayList<>();


  FigiProxy(FigiConfig figiConfig, MarketService marketService) {
    this.figiConfig = figiConfig;
    this.marketService = marketService;
    filter.add("COMMON STOCK");
    filter.add("REIT");
    filter.add("DEPOSITARY RECEIPT");
    filter.add("MUTUAL FUND");
  }

  @Autowired
  void setFigiGateway(FigiGateway figiGateway) {
    this.figiGateway = figiGateway;
  }

  @Autowired
  void setFigiAdapter(FigiAdapter figiAdapter) {
    this.figiAdapter = figiAdapter;
  }

  @Cacheable("asset.ext")
  @RateLimiter(name = "figi")
  public Asset find(String marketCode, String assetCode) {
    if (figiConfig.getEnabled()) {
      Market market = marketService.getMarket(marketCode);
      String figiMarket = market.getAliases().get(FIGI);
      FigiSearch figiSearch = FigiSearch.builder()
          .exchCode(figiMarket.toUpperCase())
          .query(assetCode.toUpperCase())
          .build();
      FigiResponse response = figiGateway.search(figiSearch, figiConfig.getApiKey());
      if (response.getError() != null) {
        log.debug("Error {}/{} {}", figiMarket, assetCode, response.getError());
        return null;
      }
      if (response.getData() != null) {
        for (FigiAsset datum : response.getData()) {
          if (filter.contains(datum.getSecurityType2().toUpperCase())) {
            log.debug("Found {}/{}", figiMarket, assetCode);
            return figiAdapter.transform(market, datum);
          }
        }
      }
      log.debug("Couldn't find {}/{}", figiMarket, assetCode);
    }
    return null;
  }
}
