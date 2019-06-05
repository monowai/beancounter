package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonDeserialize(builder = Currency.CurrencyBuilder.class)
public class Currency {
  String id;
  String code;
  String name;
  String symbol;

  @JsonPOJOBuilder(withPrefix = "")
  public static class CurrencyBuilder {
  }

}
