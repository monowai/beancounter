package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class TestDateUtils {
  DateUtils dateUtils = new DateUtils();

  @Test
  void is_Today() {
    assertThat(dateUtils.isToday(dateUtils.today())).isTrue();
    assertThat(dateUtils.isToday(null)).isTrue();
    assertThat(dateUtils.isToday("")).isTrue();
    assertThat(dateUtils.isToday(" ")).isTrue();
  }

  @Test
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder()
        .setTimeZone(TimeZone.getTimeZone(dateUtils.defaultZone))
        .setInstant(new Date()).build();
    String now = dateUtils.today();
    calendar.get(Calendar.YEAR);
    assertThat(now)
        .isNotNull()
        .startsWith(String.valueOf(calendar.get(Calendar.YEAR)))
        .contains("-" + (String.format("%02d", calendar.get(Calendar.MONTH) + 1)) + "-")
        .contains("-" + String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)))
    ;
    assertThat(dateUtils.getDate("2019-11-29")).isNotNull();
    dateUtils.isValid("2019-11-29");
  }

  @Test
  void is_InvalidDateDetected() {
    String invalidDate = "2019-11-07'";
    assertThrows(BusinessException.class, () -> dateUtils.isValid(invalidDate));
  }

  @Test
  void is_NullIsoDate() {
    assertThat(dateUtils.getDate(null)).isNull();
    assertThat(dateUtils.getLocalDate(null, "yyyy-MM-dd")).isNull();
  }

  @Test
  void is_LocalDateEqualToToday() {
    String today = dateUtils.today();
    LocalDate nowInTz = LocalDate.now(dateUtils.defaultZone);
    assertThat(nowInTz.toString()).isEqualTo(today);
  }

  @Test
  void is_FridayFoundFromSundayInSystemDefaultTz() {
    LocalDate sunday = getSunday();
    LocalDate found = dateUtils.getLastMarketDate(sunday, ZoneId.systemDefault());
    assertThat(dateUtils.convert(found)).isEqualTo(getFriday());
  }

  @Test
  void is_WeekendFound() {
    LocalDate zonedDateTime = getSunday();

    assertThat(dateUtils.isWorkDay(zonedDateTime)).isFalse();// Sunday

    zonedDateTime = getSaturday();
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isFalse(); // Saturday

    zonedDateTime = getFriday();
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isTrue(); // Friday

    zonedDateTime = getMonday();
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isTrue(); // Monday

  }

  @Test
  void is_MarketDataPriceDateCalculated() {
    String sgToday = "2019-11-01"; // Friday in Singapore (past date)
    LocalDate sgDateTime = dateUtils.getDate(sgToday);

    LocalDate dateResult = dateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"));

    assertThat(dateResult.toString()).isEqualTo(sgToday);

    dateResult = dateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"), 2);
    assertThat(dateResult.toString()).isEqualTo("2019-10-30");
  }

  @Test
  void is_DateString() {
    assertThat(dateUtils.getDateString(null)).isNull();
    assertThat(dateUtils.getDateString(getMonday())).isEqualTo("2019-10-21");

  }

  @Test
  void is_ParseException() {
    assertThrows(BusinessException.class, () -> dateUtils.isToday("ABC-MM-11"));
  }

  private LocalDate getMonday() {
    return dateUtils.getDate("2019-10-21");
  }

  private LocalDate getSunday() {
    return dateUtils.getDate("2019-10-20");
  }

  private LocalDate getSaturday() {
    return dateUtils.getDate("2019-10-19");
  }

  private LocalDate getFriday() {
    return dateUtils.getDate("2019-10-18");
  }

}
