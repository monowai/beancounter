package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.DateUtils;
import com.google.common.annotations.VisibleForTesting;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;

class TestDateUtils {
  @Test
  @VisibleForTesting
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder().setInstant(new Date()).build();
    String now = new DateUtils().today();
    calendar.get(Calendar.YEAR);
    assertThat(now)
        .isNotNull()
        .startsWith(String.valueOf(calendar.get(Calendar.YEAR)))
        .contains("-" + (calendar.get(Calendar.MONTH) + 1) + "-")
        .contains("-" + calendar.get(Calendar.DAY_OF_MONTH))
    ;
  }

  @Test
  @VisibleForTesting
  void is_InvalidDateDetected() {
    String invalidDate = "2019-11-07'";
    assertThrows(BusinessException.class, () -> {
      new DateUtils().isValid(invalidDate);
    });

  }
}
