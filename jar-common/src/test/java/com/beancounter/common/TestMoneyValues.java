package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.MoneyValues;
import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.Test;

class TestMoneyValues {
  @VisibleForTesting
  @Test
  void is_DefaultMoneyValuesSet() {
    MoneyValues moneyValues = MoneyValues.builder()
        .currency(Currency.builder().code("USD").build())
        .build();

    assertThat(moneyValues).hasNoNullFieldsOrProperties();
  }
}
