package com.beancounter.marketdata.providers.wtd;

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
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Facade for WorldTradingData.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Service
@Slf4j
public class WtdService implements MarketDataProvider {
  public static final String ID = "WTD";
  @Value("${beancounter.marketdata.provider.WTD.key:demo}")
  private String apiKey;
  private final WtdProxy wtdProxy;
  private final WtdConfig wtdConfig;
  private final WtdAdapter wtdAdapter;

  @Autowired
  WtdService(WtdProxy wtdProxy,
             WtdConfig wtdConfig,
             WtdAdapter wtdAdapter) {
    this.wtdProxy = wtdProxy;
    this.wtdConfig = wtdConfig;
    this.wtdAdapter = wtdAdapter;
  }

  @PostConstruct
  public void logStatus() {
    log.info("APIKey is [{}}", (apiKey.equalsIgnoreCase("DEMO") ? "DEMO" : "DEFINED"));
  }

  @Override
  public Collection<MarketData> getMarketData(PriceRequest priceRequest) {

    ProviderArguments providerArguments =
        ProviderArguments.getInstance(priceRequest, wtdConfig);

    Map<Integer, Future<WtdResponse>> batchedRequests = new ConcurrentHashMap<>();

    for (Integer batch : providerArguments.getBatch().keySet()) {
      batchedRequests.put(batch,
          wtdProxy.getPrices(
              providerArguments.getBatch().get(batch),
              providerArguments.getBatchConfigs().get(batch).getDate(),
              apiKey));
    }
    log.trace("Assets price processing complete.");
    return getMarketData(providerArguments, batchedRequests);
  }

  private Collection<MarketData> getMarketData(ProviderArguments providerArguments,
                                               Map<Integer, Future<WtdResponse>> requests) {

    Collection<MarketData> results = new ArrayList<>();
    boolean empty = requests.isEmpty();

    while (!empty) {
      for (Integer batch : requests.keySet()) {
        if (requests.get(batch).isDone()) {
          results.addAll(wtdAdapter.get(providerArguments, batch, requests.get(batch)));
          requests.remove(batch);
        }
        empty = requests.isEmpty();
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
    if (wtdConfig.getMarkets() == null) {
      return false;
    }
    return wtdConfig.getMarkets().contains(market.getCode());

  }

  @Override
  public LocalDate getDate(Market market, PriceRequest priceRequest) {
    return wtdConfig.getMarketDate(market, priceRequest.getDate());
  }


}
