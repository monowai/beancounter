package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.MarketDataAdapter;
import com.beancounter.marketdata.providers.ProviderArguments;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AlphaAdapter implements MarketDataAdapter {
  private final ObjectMapper alphaMdMapper;

  public AlphaAdapter() {
    alphaMdMapper = new ObjectMapper();

    SimpleModule module =
        new SimpleModule("AlphaMarketDataDeserializer",
            new Version(1, 0, 0, null, null, null));
    module.addDeserializer(MarketData.class, new AlphaDeserializer());
    alphaMdMapper.registerModule(module);
  }

  public Collection<MarketData> get(ProviderArguments providerArguments,
                                    Integer batchId, String response) {

    Collection<MarketData> results = new ArrayList<>();
    try {

      String[] assets = providerArguments.getAssets(batchId);

      for (String dpAsset : assets) {
        Asset asset = providerArguments.getDpToBc().get(dpAsset);
        if (isMdResponse(asset, response)) {
          MarketData marketData = alphaMdMapper.readValue(response, MarketData.class);
          String assetName = marketData.getAsset().getName();
          asset.setName(assetName); // Keep the name
          marketData.setAsset(asset); // Return BC view of the asset, not MarketProviders
          log.debug("Valued {} ", marketData.getAsset().getName());
          results.add(marketData);
        } else {
          results.add(getDefault(asset));
        }
      }

    } catch (IOException e) {
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

  public ObjectMapper getAlphaObjectMapper() {
    return alphaMdMapper;
  }

}
