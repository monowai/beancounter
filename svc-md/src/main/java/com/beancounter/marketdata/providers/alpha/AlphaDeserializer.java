package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.util.Dates;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;

/**
 * Deserializes a BC MarketData object from a AlphaVantage result.
 * Only returns the "latest" price.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Log4j2
public class AlphaDeserializer extends JsonDeserializer {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode source = p.getCodec().readTree(p);

    JsonNode nodeValue = source.get("Meta Data");
    Asset asset = getAsset(nodeValue);
    if (asset == null) {
      return null;
    }
    String timeZone = getTimeZone(nodeValue);

    nodeValue = source.get("Time Series (Daily)");

    MapType mapType = mapper.getTypeFactory()
        .constructMapType(LinkedHashMap.class, String.class, HashMap.class);


    LinkedHashMap<?, ? extends LinkedHashMap<String, Object>>
        allValues = mapper.readValue(nodeValue.toString(), mapType);

    MarketData marketData = null;

    Optional<? extends Map.Entry<?, ? extends LinkedHashMap<String, Object>>>
        firstKey = allValues.entrySet().stream().findFirst();

    if (firstKey.isPresent()) {
      Date priceDate = Dates.getDate(firstKey.get().getKey().toString(), timeZone);
      marketData = getMarketData(asset, priceDate, firstKey.get().getValue());
    }
    return marketData;
  }

  private String getTimeZone(JsonNode nodeValue) {
    return  nodeValue.get("5. Time Zone").asText();
  }

  private MarketData getMarketData(Asset asset, Date priceDate, Map<String, Object> data) {
    MarketData marketData = null;
    if (data != null) {
      BigDecimal open = new BigDecimal(data.get("1. open").toString());
      BigDecimal high = new BigDecimal(data.get("2. high").toString());
      BigDecimal low = new BigDecimal(data.get("3. low").toString());
      BigDecimal close = new BigDecimal(data.get("4. close").toString());
      marketData = MarketData.builder()
          .asset(asset)
          .date(priceDate)
          .open(open)
          .close(close)
          .high(high)
          .low(low)
          .build();
    }
    return marketData;
  }

  private Asset getAsset(JsonNode nodeValue) {
    Asset asset = null;
    if (!isNull(nodeValue)) {
      nodeValue = nodeValue.get("2. Symbol");
      String[] values = nodeValue.asText().split(":");
      Market market = Market.builder().code("US").build();

      if (values.length > 1) {
        // We have a market
        market = Market.builder().code(values[1]).build();
      }

      asset = Asset.builder().code(values[0])
          .market(market)
          .build();
    }
    return asset;
  }

  private boolean isNull(JsonNode nodeValue) {
    return nodeValue == null || nodeValue.isNull() || nodeValue.asText().equals("null");
  }

}
