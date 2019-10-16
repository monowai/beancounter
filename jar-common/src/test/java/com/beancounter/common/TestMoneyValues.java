package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.utils.DateUtils;
import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.Test;

class TestMoneyValues {
  @VisibleForTesting
  @Test
  void is_DefaultMoneyValuesSet() {
    DateUtils dateUtils = new DateUtils();
    MoneyValues moneyValues = MoneyValues.builder()
        .asAt(dateUtils.getDate("2012-01-01", "yyyy-MM-dd"))
        .build();

    assertThat(moneyValues).hasNoNullFieldsOrProperties();
  }
}
