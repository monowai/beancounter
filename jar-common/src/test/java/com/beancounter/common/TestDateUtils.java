package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.DateUtils;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;

class TestDateUtils {

  @Test
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder().setInstant(new Date()).build();
    DateUtils dateUtils = new DateUtils();
    String now = dateUtils.today();
    calendar.get(Calendar.YEAR);
    assertThat(now)
        .isNotNull()
        .startsWith(String.valueOf(calendar.get(Calendar.YEAR)))
        .contains("-" + (calendar.get(Calendar.MONTH) + 1) + "-")
        .contains("-" + calendar.get(Calendar.DAY_OF_MONTH))
    ;
    assertThat(dateUtils.getDate("2019-11-29")).isNotNull();
    dateUtils.isValid("2019-11-29");
  }

  @Test
  void is_InvalidDateDetected() {
    String invalidDate = "2019-11-07'";
    assertThrows(BusinessException.class, () -> {
      new DateUtils().isValid(invalidDate);
    });
  }

  @Test
  void is_NullIsoDate() {
    String nullString = null;
    assertThat(new DateUtils().getDate(nullString)).isNull();
    assertThat(new DateUtils().getLocalDate(nullString, "yyyy-MM-dd")).isNull();
    Date nullDate = null;
    assertThat(new DateUtils().getDate(nullDate)).isNull();
  }

}
