package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
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
  BigDecimal dividends = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal costValue = BigDecimal.ZERO;
  // Internal accounting - tracks the basis used for computing cost.
  @Builder.Default
  BigDecimal costBasis = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal fees = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal purchases = BigDecimal.ZERO;
  @Builder.Default
  BigDecimal sales = BigDecimal.ZERO;

  @Builder.Default
  BigDecimal averageCost = BigDecimal.ZERO;

  /**
   * How much gain has been realised for the position.
   */
  @Builder.Default
  BigDecimal realisedGain = BigDecimal.ZERO;

  @Builder.Default
  BigDecimal unrealisedGain = BigDecimal.ZERO;

  @Builder.Default
  BigDecimal totalGain = BigDecimal.ZERO;

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class MoneyValuesBuilder {
  }

}
