package com.beancounter.marketdata.providers.alpha;

import static com.beancounter.marketdata.providers.ProviderArguments.getInstance;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
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
public class AlphaService implements MarketDataProvider {
  public static final String ID = "ALPHA";
  @Value("${beancounter.marketdata.provider.ALPHA.key:demo}")
  private String apiKey;

  private ObjectMapper alphaMdMapper;
  private AlphaRequester alphaRequester;
  private AlphaConfig alphaConfig;

  @Autowired
  void setAlphaRequester(AlphaRequester alphaRequester) {
    this.alphaRequester = alphaRequester;
  }

  @Autowired
  void setAlphaConfig(AlphaConfig alphaConfig) {
    this.alphaConfig = alphaConfig;
  }

  @PostConstruct
  public void logStatus() {
    this.alphaMdMapper = getAlphaObjectMapper();
    log.info("Running with apiKey {}{}", apiKey.substring(0, 4).toUpperCase(),
        apiKey.equalsIgnoreCase("demo") ? "" : "***");

  }

  public ObjectMapper getAlphaObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    SimpleModule module =
        new SimpleModule("AlphaMarketDataDeserializer",
            new Version(1, 0, 0, null, null, null));
    module.addDeserializer(MarketData.class, new AlphaDeserializer());
    mapper.registerModule(module);
    return mapper;
  }

  @Override
  public MarketData getCurrent(Asset asset) {
    Collection<Asset> assets = new ArrayList<>();
    assets.add(asset);
    return getCurrent(assets).iterator().next();
  }

  @Override
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {

    ProviderArguments providerArguments = getInstance(assets, alphaConfig);

    Map<Integer, Future<String>> requests = new ConcurrentHashMap<>();

    for (Integer batchId : providerArguments.getBatch().keySet()) {
      requests.put(batchId,
          alphaRequester.getMarketData(providerArguments.getBatch().get(batchId), apiKey));
    }

    return getMarketData(providerArguments, requests);

  }

  private Collection<MarketData> getMarketData(ProviderArguments providerArguments,
                                               Map<Integer, Future<String>> requests) {
    Collection<MarketData> results = new ArrayList<>();
    boolean empty = requests.isEmpty();

    while (!empty) {
      for (Integer batch : requests.keySet()) {
        if (requests.get(batch).isDone()) {
          results.addAll(getFromResponse(providerArguments, batch, requests.get(batch)));
          requests.remove(batch);
        }
        empty = requests.isEmpty();
      }
    }

    return results;
  }

  private Collection<MarketData> getFromResponse(ProviderArguments providerArguments,
                                                 Integer batchId, Future<String> response) {

    Collection<MarketData> results = new ArrayList<>();
    try {

      String[] assets = providerArguments.getAssets(batchId);

      for (String dpAsset : assets) {
        Asset asset = providerArguments.getDpToBc().get(dpAsset);
        String result = response.get();
        if (!isMdResponse(asset, result)) {
          results.add(getDefault(asset));
        } else {
          MarketData marketData = alphaMdMapper.readValue(response.get(), MarketData.class);

          String assetName = marketData.getAsset().getName();
          asset.setName(assetName); // Keep the name
          marketData.setAsset(asset); // Return BC view of the asset, not MarketProviders
          log.debug("Valued {} ", marketData.getAsset());
          results.add(marketData);
        }
      }

    } catch (IOException | InterruptedException | ExecutionException e) {
      throw new SystemException(e.getMessage());
    }
    return results;

  }

  private boolean isMdResponse(Asset asset, String result) throws IOException {
    String field = null;
    if (result.contains("Error Message")) {
      field = "Error Message";
    } else if (result.contains("\"Note\":")) {
      field = "Note";
    } else if (result.contains("\"Information\":")) {
      field = "Information";
    }

    if (field != null) {
      JsonNode resultMessage = alphaMdMapper.readTree(result);
      log.error("{} - API returned [{}]", asset, resultMessage.get(field));
      return false;
    }
    return true;

  }

  private MarketData getDefault(Asset asset) {
    return MarketData.builder().asset(asset).close(BigDecimal.ZERO).build();
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isMarketSupported(Market market) {
    return alphaConfig.isMarketSupported(market);
  }


}
