package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import lombok.Builder;
import lombok.Data;

/**
 * A stock exchange.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@JsonDeserialize(builder = Market.MarketBuilder.class)
public class Market {
  private String code;
  private String currency;
  private TimeZone timezone;

  @SuppressWarnings("UnusedAssignment")
  @Builder.Default
  private Map<String,String> aliases = new HashMap<>();


  @JsonPOJOBuilder(withPrefix = "")
  public static class MarketBuilder {
  }

}
