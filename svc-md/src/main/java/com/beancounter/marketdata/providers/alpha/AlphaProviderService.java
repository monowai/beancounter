package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collection;
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
public class AlphaProviderService implements MarketDataProvider {
  @Value("${com.beancounter.marketdata.provider.alpha.key:not-defined}")
  private String apiKey;
  private AlphaRequest alphaRequest;
  ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  AlphaProviderService(AlphaRequest alphaRequest) {
    this.alphaRequest = alphaRequest;
  }

  @Override
  public MarketData getCurrent(Asset asset) {
    String marketCode = asset.getMarket().getCode();
    if (marketCode.equalsIgnoreCase("NASDAQ") || marketCode.equalsIgnoreCase("NYSE")) {
      marketCode = null;
    }

    String assetCode = asset.getCode();
    if (marketCode != null) {
      assetCode = assetCode + "." + marketCode;
    }

    String response = alphaRequest.getMarketData(assetCode, apiKey);
    try {
      return objectMapper.readValue(response, AlphaResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    return null;
  }

  @Override
  public String getId() {
    return "ALPHA";
  }
}
