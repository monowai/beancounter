package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Deserialize various AlphaVantage responses to a normalised PriceResponse.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Slf4j
public class AlphaDeserializer extends JsonDeserializer<PriceResponse> {
  public static final String GLOBAL_QUOTE = "Global Quote";
  public static final String TIME_SERIES_DAILY = "Time Series (Daily)";
  private static final ObjectMapper mapper = new ObjectMapper();
  private final DateUtils dateUtils = new DateUtils();

  @Override
  public PriceResponse deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
    JsonNode source = p.getCodec().readTree(p);
    if (source.has(TIME_SERIES_DAILY)) {
      return handleTimeSeries(source);
    } else if (source.has(GLOBAL_QUOTE)) {
      return handleGlobal(source);
      //nodeValue = source.get("Global Quote");
      //marketData =getMarketData(asset, )
    }
    return null;
  }

  private PriceResponse handleGlobal(JsonNode source) throws JsonProcessingException {
    JsonNode metaData = source.get("Global Quote");
    Asset asset = getAsset(metaData, "01. symbol");
    MapType mapType = mapper.getTypeFactory()
        .constructMapType(LinkedHashMap.class, String.class, String.class);

    return getMdFromGlobal(asset, mapper.readValue(metaData.toString(), mapType));
  }

  private PriceResponse getMdFromGlobal(Asset asset, Map<String, Object> data) {
    Collection<MarketData> marketData = new ArrayList<>();
    if (data != null) {
      BigDecimal open = new BigDecimal(data.get("02. open").toString());
      BigDecimal high = new BigDecimal(data.get("03. high").toString());
      BigDecimal low = new BigDecimal(data.get("04. low").toString());
      BigDecimal close = new BigDecimal(data.get("05. price").toString());
      Integer volume = Integer.decode(data.get("06. volume").toString());
      String priceDate = data.get("07. latest trading day").toString();
      BigDecimal previousClose = MathUtils.get(data.get("08. previous close").toString());
      BigDecimal change = MathUtils.get(data.get("09. change").toString());
      marketData.add(MarketData.builder()
          .asset(asset)
          .priceDate(dateUtils.getDate(priceDate))
          .open(open)
          .close(close)
          .high(high)
          .low(low)
          .volume(volume)
          .previousClose(previousClose)
          .change(change)
          .changePercent(MathUtils.changePercent(change, previousClose))
          .build());
    }
    return PriceResponse.builder().data(marketData).build();
  }

  private PriceResponse handleTimeSeries(JsonNode source) throws JsonProcessingException {
    Collection<MarketData> results = new ArrayList<>();
    JsonNode metaData = source.get("Meta Data");
    Asset asset = getAsset(metaData, "2. Symbol");
    if (asset != null) {
      MapType mapType = mapper.getTypeFactory()
          .constructMapType(LinkedHashMap.class, String.class, HashMap.class);
      LinkedHashMap<?, ? extends LinkedHashMap<String, Object>>
          allValues = mapper.readValue(source.get("Time Series (Daily)").toString(), mapType);

      // ToDo: This is not supporting "as at dates" and just returns the head
      for (Object key : allValues.keySet()) {
        Map<String, Object> rawData = allValues.get(key.toString());
        LocalDate localDateTime = dateUtils.getLocalDate(
            key.toString(), "yyyy-M-dd");
        results.add(getPrice(asset, localDateTime, rawData));
      }

    }
    return PriceResponse.builder().data(results).build();
  }

  private MarketData getPrice(Asset asset, LocalDate priceDate, Map<String, Object> data) {
    MarketData marketData = null;
    if (data != null) {
      BigDecimal open = new BigDecimal(data.get("1. open").toString());
      BigDecimal high = new BigDecimal(data.get("2. high").toString());
      BigDecimal low = new BigDecimal(data.get("3. low").toString());
      BigDecimal close = new BigDecimal(data.get("4. close").toString());
      marketData = MarketData.builder()
          .asset(asset)
          .priceDate(priceDate)
          .open(open)
          .close(close)
          .high(high)
          .low(low)
          .build();
    }
    return marketData;
  }

  private Asset getAsset(JsonNode nodeValue, String assetField) {
    Asset asset = null;
    if (!isNull(nodeValue)) {
      JsonNode symbols = nodeValue.get(assetField);
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
