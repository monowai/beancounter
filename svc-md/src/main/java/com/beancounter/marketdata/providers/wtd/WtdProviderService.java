package com.beancounter.marketdata.providers.wtd;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.service.MarketConfig;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.util.Dates;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TimeZone;
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
public class WtdProviderService implements MarketDataProvider {
  public static final String ID = "WTD";
  @Value("${beancounter.marketdata.provider.WTD.key:demo}")
  private String apiKey;
  @Value("${beancounter.marketdata.provider.WTD.batchSize:2}")
  private Integer batchSize;

  @Value("${beancounter.marketdata.provider.WTD.date:#{null}}")
  private String date;

  @Value("${beancounter.marketdata.provider.WTD.markets}")
  private String markets;

  private Dates dates;

  private WtdRequestor wtdRequestor;

  private MarketConfig marketConfig;

  private TimeZone timeZone = TimeZone.getTimeZone("US/Eastern");

  @Autowired
  WtdProviderService(WtdRequestor wtdRequestor, MarketConfig marketConfig, Dates dates) {
    this.wtdRequestor = wtdRequestor;
    this.dates = dates;
    this.marketConfig = marketConfig;
  }

  @PostConstruct
  void logStatus() {
    log.info("Market Date is [{}]", (date == null ? "calculated" : date));
    log.info("APIKey is [{}}", (apiKey.equalsIgnoreCase("DEMO")? "DEMO": "DEFINED"));
  }

  @Override
  public MarketData getCurrent(Asset asset) {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(asset);
    Collection<MarketData> response = getCurrent(assets);

    return response.iterator().next();
  }

  @Override
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    String date = getDate();
    log.info("Asset Prices as at [{}]", date);
    ProviderArguments providerArguments =
        ProviderArguments.getInstance(assets, this);

    Map<Integer, Future<WtdResponse>> batchedRequests = new ConcurrentHashMap<>();

    for (Integer integer : providerArguments.getBatch().keySet()) {
      batchedRequests.put(integer,
          wtdRequestor.getMarketData(providerArguments.getBatch().get(integer), date, apiKey));
    }
    log.info("Assets price retrieval completed.");
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
      for (String dpAsset : assets) {

        // Ensure we return a MarketData result for each requested asset
        Asset bcAsset = providerArguments.getDpToBc().get(dpAsset);
        if (wtdResponse.getMessage() != null) {
          // Entire call failed
          log.error("{} - {}", wtdResponse.getMessage(), providerArguments.getAssets(batchId));
          if (wtdResponse.getData() == null) {
            throw new BusinessException(wtdResponse.getMessage());
          }
        }

        MarketData marketData = null;
        if (wtdResponse.getData() != null) {
          marketData = wtdResponse.getData().get(dpAsset);
        }

        if (marketData == null) {
          // Not contained in the response
          marketData = getDefault(bcAsset, dpAsset);
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


  private MarketData getDefault(Asset asset, String dpAsset) {
    log.warn("Unable to locate a price on {} for {} using code {}. Returning a default",
        getDate(), asset, dpAsset);

    return MarketData.builder()
        .asset(asset)
        .close(BigDecimal.ZERO).build();
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Integer getBatchSize() {
    return batchSize;
  }

  @Override
  public Boolean isMarketSupported(Market market) {
    if (markets == null) {
      return false;
    }
    return markets.contains(market.getCode());

  }

  @Override
  public String getMarketProviderCode(Market market) {
    // Don't trust the caller
    return marketConfig.getMarketMap().get(market.getCode()).getAliases().get(ID);

  }

  @Override
  public String getDate() {
    if (date != null) {
      return date;
    }
    LocalDate result = dates.getLastMarketDate(
        Instant.now().atZone(ZoneId.systemDefault()),
        timeZone.toZoneId());

    return result.toString();
  }


}
