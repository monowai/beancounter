package com.beancounter.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;


/**
 * Accumulation of quantities.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class QuantityValues {
  @Builder.Default
  private BigDecimal sold = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal purchased = BigDecimal.ZERO;

  @Builder.Default
  private BigDecimal adjustment = BigDecimal.ZERO;

  private BigDecimal total;

  public BigDecimal getTotal() {
    return (purchased.add(sold)).add(adjustment);
  }

}
