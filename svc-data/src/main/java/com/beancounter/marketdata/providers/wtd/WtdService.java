package com.beancounter.marketdata.providers.wtd;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.BatchConfig;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.service.MarketDataProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AlphaAdvantage - www.alphavantage.co.
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
  private WtdRequester wtdRequester;
  private WtdConfig wtdConfig;


  @Autowired
  WtdService(WtdRequester wtdRequester, WtdConfig wtdConfig) {
    this.wtdRequester = wtdRequester;
    this.wtdConfig = wtdConfig;
  }

  @PostConstruct
  public void logStatus() {
    log.info("APIKey is [{}}", (apiKey.equalsIgnoreCase("DEMO") ? "DEMO" : "DEFINED"));
  }

  @Override
  public MarketData getPrices(Asset asset) {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(asset);

    Collection<MarketData> response = getPrices(PriceRequest.builder().assets(assets).build());

    return response.iterator().next();
  }

  @Override
  public Collection<MarketData> getPrices(PriceRequest priceRequest) {

    ProviderArguments providerArguments =
        ProviderArguments.getInstance(priceRequest, wtdConfig);

    Map<Integer, Future<WtdResponse>> batchedRequests = new ConcurrentHashMap<>();

    for (Integer batch : providerArguments.getBatch().keySet()) {
      batchedRequests.put(batch,
          wtdRequester.getMarketData(
              providerArguments.getBatch().get(batch),
              providerArguments.getBatchConfigs().get(batch).getDate(),
              apiKey));
    }
    log.debug("Assets price retrieval completed.");
    return getMarketData(providerArguments, batchedRequests);
  }

  private Collection<MarketData> getMarketData(ProviderArguments providerArguments,
                                               Map<Integer, Future<WtdResponse>> requests) {

    Collection<MarketData> results = new ArrayList<>();
    boolean empty = requests.isEmpty();

    while (!empty) {
      for (Integer batch : requests.keySet()) {
        if (requests.get(batch).isDone()) {
          results.addAll(extract(providerArguments, batch, requests.get(batch)));
          requests.remove(batch);
        }
        empty = requests.isEmpty();
      }
    }

    return results;

  }

  private Collection<MarketData> extract(ProviderArguments providerArguments,
                                         Integer batchId, Future<WtdResponse> response) {
    Collection<MarketData> results = new ArrayList<>();
    try {
      WtdResponse wtdResponse = response.get();

      String[] assets = providerArguments.getAssets(batchId);
      BatchConfig batchConfig = providerArguments.getBatchConfigs().get(batchId);
      for (String dpAsset : assets) {

        // Ensure we return a MarketData result for each requested asset
        Asset bcAsset = providerArguments.getDpToBc().get(dpAsset);
        if (wtdResponse.getMessage() != null) {
          // Issue with the data
          // ToDo: subtract a day and try again?
          log.warn("{} - {}", wtdResponse.getMessage(), providerArguments.getAssets(batchId));
        }

        MarketData marketData = null;
        if (wtdResponse.getData() != null) {
          marketData = wtdResponse.getData().get(dpAsset);
        }

        if (marketData == null) {
          // Not contained in the response
          marketData = getDefault(bcAsset, dpAsset, batchConfig);
        } else {
          marketData.setAsset(bcAsset);
        }
        marketData.setDate(wtdResponse.getDate());
        results.add(marketData);
      }
      return results;
    } catch (InterruptedException | ExecutionException e) {
      throw new SystemException(e.getMessage());
    }
  }


  private MarketData getDefault(Asset asset, String dpAsset, BatchConfig batchConfig) {
    log.warn("Unable to locate a price on {} for {} using code {}. Returning a default",
        batchConfig.getDate(), asset, dpAsset);

    return MarketData.builder()
        .asset(asset)
        .close(BigDecimal.ZERO).build();
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


}
