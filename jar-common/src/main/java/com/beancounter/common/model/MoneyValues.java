package com.beancounter.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;


/**
 * Money Values which are used in deriving various values for financial calculations.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class MoneyValues {

  @Builder.Default
  private BigDecimal dividends = BigDecimal.ZERO;
  @Builder.Default
  // Cost in Currency terms
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
  private BigDecimal marketValue = BigDecimal.ZERO;

  private BigDecimal weight;

  private PriceData priceData;

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

  private Currency currency;

}
