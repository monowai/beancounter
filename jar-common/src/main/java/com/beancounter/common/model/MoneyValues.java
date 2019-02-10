package com.beancounter.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;


/**
 * Money Values which are used in deriving various values for financial calculations.
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@JsonDeserialize(builder = MoneyValues.MoneyValuesBuilder.class)
public class MoneyValues {

  @Builder.Default
  BigDecimal dividends = new BigDecimal(0d);
  @Builder.Default
  BigDecimal marketCost = new BigDecimal(0d); // Cost in Local Market terms
  @Builder.Default
  BigDecimal fees = new BigDecimal(0d);

  @SuppressWarnings("WeakerAccess")
  @JsonPOJOBuilder(withPrefix = "")
  public static class MoneyValuesBuilder {


  }

}
