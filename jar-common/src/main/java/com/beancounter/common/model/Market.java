package com.beancounter.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Market {
  private String code;
  private Currency currency;
  private TimeZone timezone;

  @JsonIgnore
  private Map<String, String> aliases;


  @JsonPOJOBuilder(withPrefix = "")
  public static class MarketBuilder {
  }

}
