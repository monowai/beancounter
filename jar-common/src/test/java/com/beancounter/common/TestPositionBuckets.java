package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.MoneyValues;
import org.junit.jupiter.api.Test;

class TestPositionBuckets {

  @Test
  void is_DefaultMoneyValuesSet() {
    MoneyValues moneyValues = MoneyValues.builder()
        .currency(Currency.builder().code("USD").build())
        .build();

    assertThat(moneyValues).hasNoNullFieldsOrPropertiesExcept("priceData");
  }
}
