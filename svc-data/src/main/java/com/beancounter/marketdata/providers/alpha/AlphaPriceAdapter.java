package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.contracts.AssetSearchResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.MathUtils;
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
public class AlphaPriceAdapter implements MarketDataAdapter {
  private final ObjectMapper alphaMapper = new ObjectMapper();

  public AlphaPriceAdapter() {
    SimpleModule module =
        new SimpleModule("AlphaMarketDataDeserializer",
            new Version(1, 0, 0, null, null, null));
    module.addDeserializer(PriceResponse.class, new AlphaPriceDeserializer());
    module.addDeserializer(AssetSearchResponse.class, new AlphaSearchDeserializer());
    alphaMapper.registerModule(module);
  }

  public Collection<MarketData> get(ProviderArguments providerArguments,
                                    Integer batchId, String response) {

    Collection<MarketData> results = new ArrayList<>();
    try {

      String[] assets = providerArguments.getAssets(batchId);

      for (String dpAsset : assets) {
        Asset asset = providerArguments.getDpToBc().get(dpAsset);
        if (isMdResponse(asset, response)) {
          PriceResponse priceResponse = alphaMapper.readValue(response, PriceResponse.class);
          if (priceResponse != null) {
            for (MarketData marketData : priceResponse.getData()) {
              marketData.setAsset(asset); // Return BC view of the asset, not MarketProviders
              normalise(asset.getMarket(), marketData);
              log.trace("Valued {} ", asset.getName());
              results.add(marketData);
            }
          } else {
            results.add(getDefault(asset));
          }
        } else {
          results.add(getDefault(asset));
        }
      }

    } catch (IOException e) {
      throw new SystemException(e.getMessage());
    }
    return results;

  }

  private void normalise(Market market, MarketData marketData) {
    if (market.getMultiplier().compareTo(BigDecimal.ONE) != 0) {

      marketData.setClose(
          MathUtils.multiply(marketData.getClose(), market.getMultiplier(), 4));
      marketData.setOpen(
          MathUtils.multiply(marketData.getOpen(), market.getMultiplier(), 4));
      marketData.setHigh(
          MathUtils.multiply(marketData.getHigh(), market.getMultiplier(), 4));
      marketData.setLow(
          MathUtils.multiply(marketData.getLow(), market.getMultiplier(), 4));
      marketData.setPreviousClose(
          MathUtils.multiply(marketData.getPreviousClose(), market.getMultiplier(), 4));
      marketData.setChange(
          MathUtils.multiply(marketData.getChange(), market.getMultiplier(), 4));
    }
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
      JsonNode resultMessage = alphaMapper.readTree(result);
      log.debug("API returned [{}] for {}", resultMessage.get(field), asset);
      return false;
    }
    return true;

  }

  private MarketData getDefault(Asset asset) {
    return MarketData.builder().asset(asset).build();
  }

  public ObjectMapper getAlphaMapper() {
    return alphaMapper;
  }

}
