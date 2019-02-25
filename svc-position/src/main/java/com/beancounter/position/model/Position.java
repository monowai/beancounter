package com.beancounter.position.model;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * Represents an Asset held in a Portfolio.
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@JsonDeserialize(builder = Position.PositionBuilder.class)
public class Position {
  private Asset asset;
  @Builder.Default
  @Getter
  private MoneyValues moneyValues = MoneyValues.builder().build();

  @Builder.Default
  @Getter
  private QuantityValues quantityValues = QuantityValues.builder().build();

  private MarketValue marketValue;
  
  private Date lastDate;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class PositionBuilder {

  }

}
