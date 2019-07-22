package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = Currency.CurrencyBuilder.class)
public class Currency {
  private String id;
  private String code;
  private String name;
  private String symbol;

  /**
   * Convert the configured properties to an object.
   *
   * @param id              Primary Key
   * @param stringObjectMap object
   * @return hydrated currency
   */
  public static Currency of(String id, Map<String, Object> stringObjectMap) {
    return Currency.builder()
        .id(id)
        .code(stringObjectMap.get("code").toString())
        .name(stringObjectMap.get("name").toString())
        .symbol(stringObjectMap.get("symbol").toString())
        .build();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class CurrencyBuilder {
  }

}
