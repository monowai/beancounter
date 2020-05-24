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

  private Integer precision;
  private BigDecimal total;

  public BigDecimal getTotal() {
    return (purchased.add(sold)).add(adjustment);
  }

  public Integer getPrecision() {
    // This is a bit hacky. Should be derived from the asset and set not computed
    if (getTotal() == null) {
      return 0;
    }
    if (precision != null) {
      return precision;
    }
    return (getTotal().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0 ? 0 : 3);
  }

}
