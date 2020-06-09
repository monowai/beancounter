package com.beancounter.marketdata.providers.alpha;

import static com.beancounter.marketdata.providers.ProviderArguments.getInstance;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.service.MarketDataProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import lombok.SneakyThrows;
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
  private final DateUtils dateUtils = new DateUtils();
  @Value("${beancounter.marketdata.provider.ALPHA.key:demo}")
  private String apiKey;
  private AlphaProxyCache alphaProxyCache;
  private AlphaPriceAdapter alphaPriceAdapter;

  public AlphaService(AlphaConfig alphaConfig) {
    this.alphaConfig = alphaConfig;
  }

  @Autowired
  void setAlphaHelpers(AlphaProxyCache alphaProxyCache, AlphaPriceAdapter alphaPriceAdapter) {
    this.alphaProxyCache = alphaProxyCache;
    this.alphaPriceAdapter = alphaPriceAdapter;
  }

  @PostConstruct
  void logStatus() {
    Boolean isDemo = apiKey.substring(0, 4).equalsIgnoreCase("demo");
    log.info("DEMO key is {}", isDemo);
  }

  private boolean isCurrent(String date) {
    return dateUtils.isToday(date);
  }

  @Override
  public Collection<MarketData> getMarketData(PriceRequest priceRequest) {

    ProviderArguments providerArguments = getInstance(priceRequest, alphaConfig);

    Map<Integer, Future<String>> requests = new ConcurrentHashMap<>();

    for (Integer batchId : providerArguments.getBatch().keySet()) {
      String date = providerArguments.getBatchConfigs().get(batchId).getDate();
      if (isCurrent(priceRequest.getDate())) {
        requests.put(
            batchId,
            alphaProxyCache.getCurrent(providerArguments.getBatch().get(batchId), date, apiKey));
      } else {
        requests.put(
            batchId,
            alphaProxyCache.getHistoric(providerArguments.getBatch().get(batchId), date, apiKey));
      }
    }

    return getMarketData(providerArguments, requests);

  }

  @SneakyThrows
  private Collection<MarketData> getMarketData(ProviderArguments providerArguments,
                                               Map<Integer, Future<String>> requests) {
    Collection<MarketData> results = new ArrayList<>();

    while (!requests.isEmpty()) {
      for (Integer batch : requests.keySet()) {
        if (requests.get(batch).isDone()) {
          results.addAll(
              alphaPriceAdapter.get(providerArguments, batch, requests.get(batch).get())
          );
          requests.remove(batch);
        }
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

  @Override
  @SneakyThrows
  public PriceResponse backFill(Asset asset) {
    Future<String> results = alphaProxyCache.getAdjusted(asset.getCode(), apiKey);
    String json = results.get();
    PriceResponse priceResponse = alphaPriceAdapter.getAlphaMapper()
        .readValue(json, PriceResponse.class);

    for (MarketData marketData : priceResponse.getData()) {
      marketData.setSource(AlphaService.ID);
      marketData.setAsset(asset);
    }
    return priceResponse;
  }


}
