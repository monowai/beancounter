package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
    value = "beancounter.marketdata.provider.FIGI.enabled", matchIfMissing = true
)
public class FigiProxy {

  public static final String FIGI = "FIGI";
  private final FigiConfig figiConfig;
  private final Collection<String> filter = new ArrayList<>();
  private FigiGateway figiGateway;
  private FigiAdapter figiAdapter;


  FigiProxy(FigiConfig figiConfig) {
    log.info("FIGI Enabled");
    this.figiConfig = figiConfig;
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

  @RateLimiter(name = "figi")
  public Asset find(Market market, String bcAssetCode) {
    String figiCode = bcAssetCode.replace(".", "/").toUpperCase();

    String figiMarket = market.getAliases().get(FIGI);
    FigiSearch figiSearch = FigiSearch.builder()
        .exchCode(figiMarket)
        .idValue(figiCode)
        .build();
    FigiResponse response = resolve(figiSearch);

    if (response.getError() != null) {
      log.debug("Error {}/{} {}", figiMarket, figiCode, response.getError());
      if (response.getError().equalsIgnoreCase("No identifier found.")) {
        // Unknown, so don't continue to hit the service - add a name value
        return figiAdapter.transform(market, bcAssetCode);
      }
      return null;
    }

    if (response.getData() != null) {
      for (FigiAsset datum : response.getData()) {
        if (filter.contains(datum.getSecurityType2().toUpperCase())) {
          log.trace("In response to {}/{} - found {}/{}",
              market, bcAssetCode, figiMarket, figiCode);
          return figiAdapter.transform(market, bcAssetCode, datum);
        }
      }
    }
    log.debug("Couldn't find {}/{} - as {}/{}", market, bcAssetCode, figiMarket, figiCode);
    return null;
  }

  private FigiResponse resolve(FigiSearch figiSearch) {
    FigiResponse response = findEquity(figiSearch);
    if (response.getError() != null) {
      response = findAdr(figiSearch);
      if (response.getError() != null) {
        response = findMutualFund(figiSearch);
        if (response.getError() != null) {
          response = findReit(figiSearch);
        }
      }
    }
    return response;
  }

  private Collection<FigiSearch> getSearchArgs(FigiSearch figiSearch) {
    Collection<FigiSearch> figiSearches = new ArrayList<>();
    figiSearches.add(figiSearch);
    return figiSearches;
  }

  private FigiResponse extractResult(Collection<FigiResponse> search) {
    if (search.isEmpty()) {
      return null;
    }
    return search.iterator().next();
  }

  private FigiResponse findEquity(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Common Stock");
    return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.getApiKey()));
  }


  private FigiResponse findMutualFund(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Mutual Fund");
    return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.getApiKey()));
  }

  private FigiResponse findReit(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("REIT");
    return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.getApiKey()));
  }

  private FigiResponse findAdr(FigiSearch figiSearch) {
    figiSearch.setSecurityType2("Depositary Receipt");
    return extractResult(figiGateway.search(getSearchArgs(figiSearch), figiConfig.getApiKey()));
  }
}
