package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Builder;
import lombok.Data;


/**
 * Money Values which are used in deriving various values for financial calculations.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@SuppressWarnings("ALL")
@Data
@Builder
@JsonDeserialize(builder = MoneyValues.MoneyValuesBuilder.class)
public class MoneyValues {

  @Builder.Default
  private BigDecimal dividends = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal costValue = BigDecimal.ZERO;
  // Internal accounting - tracks the basis used for computing cost.
  @Builder.Default
  private BigDecimal costBasis = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal fees = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal purchases = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal sales = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal price = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal marketValue = BigDecimal.ZERO;

  @Builder.Default
  private BigDecimal averageCost = BigDecimal.ZERO;

  /**
   * How much gain has been realised for the position.
   */
  @Builder.Default
  private BigDecimal realisedGain = BigDecimal.ZERO;

  @Builder.Default
  private BigDecimal unrealisedGain = BigDecimal.ZERO;

  @Builder.Default
  private BigDecimal totalGain = BigDecimal.ZERO;

  private Date asAt;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class MoneyValuesBuilder {
  }

}
