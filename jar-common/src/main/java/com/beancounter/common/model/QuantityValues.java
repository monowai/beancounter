package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import jdk.nashorn.internal.objects.annotations.Getter;
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
  BigDecimal sold = new BigDecimal(0d);
  @Builder.Default
  BigDecimal purchased = new BigDecimal(0d);

  BigDecimal total;

  @Getter
  public BigDecimal getTotal() {
    return purchased.add(sold);
  }

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class QuantityValuesBuilder {

  }
}
