package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.contracts.AssetSearchResponse;
import com.beancounter.common.contracts.AssetSearchResult;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AlphaSearchDeserializer extends JsonDeserializer<AssetSearchResponse> {
  public static final String BEST_MATCHES = "bestMatches";
  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public AssetSearchResponse deserialize(JsonParser p, DeserializationContext context)
      throws IOException {
    Collection<AssetSearchResult> results = new ArrayList<>();
    JsonNode source = p.getCodec().readTree(p);
    JsonNode metaData = source.get(BEST_MATCHES);
    if (metaData != null) {
      CollectionType collectionType = mapper.getTypeFactory()
          .constructCollectionType(ArrayList.class, HashMap.class);
      Collection<Map<String, String>> rows = mapper.readValue(
          source.get(BEST_MATCHES).toString(), collectionType);
      for (Map<String, String> row : rows) {
        results.add(AssetSearchResult.builder()
            .symbol(row.get("1. symbol"))
            .name(row.get("2. name"))
            .type(row.get("3. type"))
            .build());
      }

    }
    return AssetSearchResponse.builder().data(results).build();
  }


}
