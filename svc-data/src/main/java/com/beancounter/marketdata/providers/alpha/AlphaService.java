package com.beancounter.marketdata.providers.alpha;

import static com.beancounter.marketdata.providers.ProviderArguments.getInstance;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.service.MarketDataProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Facade. AlphaAdvantage - www.alphavantage.co.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
@Slf4j
public class AlphaService implements MarketDataProvider {
  public static final String ID = "ALPHA";
  private final AlphaConfig alphaConfig;
  @Value("${beancounter.marketdata.provider.ALPHA.key:demo}")
  private String apiKey;
  private AlphaProxyCache alphaProxyCache;
  private AlphaAdapter alphaAdapter;

  public AlphaService(AlphaConfig alphaConfig) {
    this.alphaConfig = alphaConfig;
  }

  @Autowired
  void setAlphaHelpers(AlphaProxyCache alphaProxyCache, AlphaAdapter alphaAdapter) {
    this.alphaProxyCache = alphaProxyCache;
    this.alphaAdapter = alphaAdapter;
  }

  @PostConstruct
  void logStatus() {
    Boolean isDemo = apiKey.substring(0, 4).equalsIgnoreCase("demo");
    log.info("DEMO key is {}", isDemo);
  }

  @Override
  public Collection<MarketData> getMarketData(PriceRequest priceRequest) {

    ProviderArguments providerArguments = getInstance(priceRequest, alphaConfig);

    Map<Integer, String> requests = new ConcurrentHashMap<>();

    for (Integer batchId : providerArguments.getBatch().keySet()) {
      requests.put(
          batchId,
          alphaProxyCache.getPrices(
              providerArguments.getBatch().get(batchId),
              providerArguments.getBatchConfigs().get(batchId).getDate(),
              apiKey));
    }

    return getMarketData(providerArguments, requests);

  }

  private Collection<MarketData> getMarketData(ProviderArguments providerArguments,
                                               Map<Integer, String> requests) {
    Collection<MarketData> results = new ArrayList<>();

    while (!requests.isEmpty()) {
      for (Integer batch : requests.keySet()) {
        results.addAll(
            alphaAdapter.get(providerArguments, batch, requests.get(batch))
        );
        requests.remove(batch);
      }
    }

    return results;
  }


  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isMarketSupported(Market market) {
    if (alphaConfig.getMarkets() == null) {
      return false;
    }
    return alphaConfig.getMarkets().contains(market.getCode());
  }

  @Override
  public LocalDate getDate(Market market, PriceRequest priceRequest) {
    return alphaConfig.getMarketDate(market, priceRequest.getDate());
  }


}
