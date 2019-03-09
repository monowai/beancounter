package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
public class AlphaProviderService implements MarketDataProvider {
  @Value("${com.beancounter.marketdata.provider.alpha.key:123}")
  private String apiKey;
  private AlphaRequestor alphaRequestor;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  AlphaProviderService(AlphaRequestor alphaRequestor) {
    this.alphaRequestor = alphaRequestor;
  }

  @Override
  public MarketData getCurrent(Asset asset) {
    String assetCode = getAsset(asset);

    Future<String> response = alphaRequestor.getMarketData(assetCode, apiKey);

    return getMarketData(asset, response);
  }

  @Override
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    Collection<MarketData> results = new ArrayList<>();
    Map<Asset, Future<String>> requestResults = new HashMap<>();
    for (Asset asset : assets) {
      requestResults.put(asset, alphaRequestor.getMarketData(getAsset(asset), apiKey));
    }
    for (Asset asset : requestResults.keySet()) {
      Future<String> result = requestResults.get(asset);
      if (result.isDone()) {
        results.add(getMarketData(asset, result));
        requestResults.remove(result);
      }

    }
    return results;
  }

  private MarketData getMarketData(Asset asset, Future<String> response) {
    try {
      String result = response.get();

      if (!isMdResponse(asset, result)) {
        return getDefault(asset);
      }
      MarketData alphaResponse = objectMapper.readValue(response.get(), AlphaResponse.class);
      String assetName = alphaResponse.getAsset().getName();
      asset.setName(assetName); // Keep the name
      alphaResponse.setAsset(asset); // Return BC view of the asset, not MarketProviders
      log.debug("Valued {} ", alphaResponse.getAsset());
      return alphaResponse;
    } catch (IOException | InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isMdResponse(Asset asset, String result) throws IOException {
    String field = null;
    if (result.contains("Error Message")) {
      field = "Error Message";
    } else if (result.contains("\"Note\":")) {
      field = "Note";
    }

    if (field != null) {
      JsonNode resultMessage = objectMapper.readTree(result);
      log.error("{} - API returned [{}]", asset, resultMessage.get(field));
      return false;
    }
    return true;

  }


  private MarketData getDefault(Asset asset) {
    return MarketData.builder().asset(asset).close(BigDecimal.ZERO).build();
  }

  private String getAsset(Asset asset) {
    String marketCode = asset.getMarket().getCode();
    if (marketCode.equalsIgnoreCase("NASDAQ") || marketCode.equalsIgnoreCase("NYSE")) {
      marketCode = null;
    }

    String assetCode = asset.getCode();
    if (marketCode != null) {
      assetCode = assetCode + "." + marketCode;
    }
    return assetCode;
  }

  @Override
  public String getId() {
    return "ALPHA";
  }
}
