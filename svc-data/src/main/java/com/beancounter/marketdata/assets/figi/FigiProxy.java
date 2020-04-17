package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.markets.MarketService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
    value = "beancounter.marketdata.provider.FIGI.enabled", matchIfMissing = true
)
public class FigiProxy {

  public static final String FIGI = "FIGI";
  private FigiGateway figiGateway;
  private final MarketService marketService;
  private final FigiConfig figiConfig;
  private FigiAdapter figiAdapter;
  private final Collection<String> filter = new ArrayList<>();


  FigiProxy(FigiConfig figiConfig, MarketService marketService) {
    log.info("FIGI Enabled");
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

  @Cacheable(value = "asset.ext", unless = "#result == null")
  @RateLimiter(name = "figi")
  public Asset find(String marketCode, String assetCode) {
    Market market = marketService.getMarket(marketCode);
    String figiMarket = market.getAliases().get(FIGI);
    FigiSearch figiSearch = FigiSearch.builder()
        .exchCode(figiMarket.toUpperCase())
        .query(assetCode.toUpperCase())
        .build();
    FigiResponse response = resolve(figiSearch);

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
    return null;
  }

  private FigiResponse resolve(FigiSearch figiSearch) {
    FigiResponse response = findEquity(figiSearch);
    if (response.getData().isEmpty()) {
      response = findAdr(figiSearch);
      if (response.getData().isEmpty()) {
        response = findMutualFund(figiSearch);
        if (response.getData().isEmpty()) {
          response = findReit(figiSearch);
        }
      }
    }
    return response;
  }

  private FigiResponse findEquity(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Common Stock");
    return figiGateway.search(figiSearch, figiConfig.getApiKey());
  }

  private FigiResponse findMutualFund(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Mutual Fund");
    return figiGateway.search(figiSearch, figiConfig.getApiKey());
  }

  private FigiResponse findReit(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("REIT");
    return figiGateway.search(figiSearch, figiConfig.getApiKey());
  }

  private FigiResponse findAdr(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Depositary Receipt");
    return figiGateway.search(figiSearch, figiConfig.getApiKey());
  }
}
