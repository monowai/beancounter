package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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
  String code;
  TimeZone timezone;

  @JsonPOJOBuilder(withPrefix = "")
  public static class MarketBuilder {
  }

}
