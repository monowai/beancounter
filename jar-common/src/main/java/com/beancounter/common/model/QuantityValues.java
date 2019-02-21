package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;


/**
 * Accumulation of quantities.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@JsonDeserialize(builder = QuantityValues.QuantityValuesBuilder.class)
public class QuantityValues {
  @Builder.Default
  private BigDecimal sold = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal purchased = BigDecimal.ZERO;

  @Builder.Default
  private BigDecimal adjustment = BigDecimal.ZERO;

  BigDecimal total;

  public BigDecimal getTotal() {
    return (purchased.add(sold)).add(adjustment);
  }

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class QuantityValuesBuilder {

  }
}
