package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;


/**
 * Various data points representing marketdata for an asset.
 * @author mikeh
 * @since 2019-01-27
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@JsonDeserialize(builder = MarketData.MarketDataBuilder.class)
public class MarketData {
  Asset asset;
  Date date;
  BigDecimal open;
  BigDecimal close;
  BigDecimal low;
  BigDecimal high;
  BigDecimal volume;

  @JsonPOJOBuilder(withPrefix = "")
  public static class MarketDataBuilder {

  }
}
