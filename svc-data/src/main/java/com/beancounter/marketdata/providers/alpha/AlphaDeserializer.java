package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deserialize a BC MarketData object from a AlphaVantage result.
 * Only returns the "latest" price.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Slf4j
public class AlphaDeserializer extends JsonDeserializer<MarketData> {
  private static final ObjectMapper mapper = new ObjectMapper();
  private DateUtils dateUtils = new DateUtils();

  @Autowired
  void setDateUtils(DateUtils dateUtils) {
    this.dateUtils = dateUtils;
  }

  @Override
  public MarketData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    MarketData result = null;
    JsonNode source = p.getCodec().readTree(p);

    JsonNode nodeValue = source.get("Meta Data");
    Asset asset = getAsset(nodeValue);
    if (asset != null) {
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
        LocalDate localDateTime = dateUtils.getLocalDate(
            firstKey.get().getKey().toString(), "yyyy-M-dd");
        marketData = getMarketData(asset, localDateTime, firstKey.get().getValue());
      }
      result = marketData;
    }

    return result;
  }

  private String getTimeZone(JsonNode nodeValue) {
    return nodeValue.get("5. Time Zone").asText();
  }

  private MarketData getMarketData(Asset asset, LocalDate priceDate, Map<String, Object> data) {
    MarketData marketData = null;
    if (data != null) {
      BigDecimal open = new BigDecimal(data.get("1. open").toString());
      BigDecimal high = new BigDecimal(data.get("2. high").toString());
      BigDecimal low = new BigDecimal(data.get("3. low").toString());
      BigDecimal close = new BigDecimal(data.get("4. close").toString());
      marketData = MarketData.builder()
          .asset(asset)
          .date(dateUtils.getDateString(priceDate))
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
      JsonNode symbols = nodeValue.get("2. Symbol");
      String[] values = symbols.asText().split(":");
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
