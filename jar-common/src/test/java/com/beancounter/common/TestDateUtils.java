package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;

class TestDateUtils {

  @Test
  void is_Today() {
    assertThat(DateUtils.isToday(DateUtils.today())).isTrue();
    assertThat(DateUtils.isToday(null)).isTrue();
  }

  @Test
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder().setInstant(new Date()).build();
    String now = DateUtils.today();
    calendar.get(Calendar.YEAR);
    assertThat(now)
        .isNotNull()
        .startsWith(String.valueOf(calendar.get(Calendar.YEAR)))
        .contains("-" + (String.format("%02d", calendar.get(Calendar.MONTH) + 1)) + "-")
        .contains("-" + String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)))
    ;
    assertThat(DateUtils.getDate("2019-11-29")).isNotNull();
    DateUtils.isValid("2019-11-29");
  }

  @Test
  void is_InvalidDateDetected() {
    String invalidDate = "2019-11-07'";
    assertThrows(BusinessException.class, () -> DateUtils.isValid(invalidDate));
  }

  @Test
  void is_NullIsoDate() {
    assertThat(DateUtils.getDate(null)).isNull();
    assertThat(DateUtils.getLocalDate(null, "yyyy-MM-dd")).isNull();
  }

  @Test
  void is_LocalDateEqualToToday() {
    String today = DateUtils.today();
    LocalDate nowInTz = LocalDate.now(DateUtils.defaultZone);
    assertThat(nowInTz.toString()).isEqualTo(today);
  }

  @Test
  void is_FridayFoundFromSundayInSystemDefaultTz() {
    LocalDate sunday = getSunday();
    LocalDate found = DateUtils.getLastMarketDate(sunday, ZoneId.systemDefault());
    assertThat(DateUtils.convert(found)).isEqualTo(getFriday());
  }

  @Test
  void is_WeekendFound() {
    LocalDate zonedDateTime = getSunday();

    assertThat(DateUtils.isWorkDay(zonedDateTime)).isFalse();// Sunday

    zonedDateTime = getSaturday();
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isFalse(); // Saturday

    zonedDateTime = getFriday();
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isTrue(); // Friday

    zonedDateTime = getMonday();
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isTrue(); // Monday

  }

  @Test
  void is_MarketDataPriceDateCalculated() {
    String sgToday = "2019-11-01"; // Friday in Singapore
    LocalDate sgDateTime = DateUtils.getDate(sgToday);

    LocalDate dateResult = DateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"));

    assertThat(dateResult.toString()).isEqualTo("2019-10-31"); // Boo!

    dateResult = DateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"), 2);
    assertThat(dateResult.toString()).isEqualTo("2019-10-30");
  }

  @Test
  void is_DateString() {
    //noinspection ConstantConditions
    assertThat(DateUtils.getDateString(null)).isNull();
    assertThat(DateUtils.getDateString(getMonday())).isEqualTo("2019-10-21");

  }

  @Test
  void is_ParseException() {
    assertThrows(BusinessException.class, () -> DateUtils.isToday("ABC-MM-11"));
  }

  private LocalDate getMonday() {
    return DateUtils.getDate("2019-10-21");
  }

  private LocalDate getSunday() {
    return DateUtils.getDate("2019-10-20");
  }

  private LocalDate getSaturday() {
    return DateUtils.getDate("2019-10-19");
  }

  private LocalDate getFriday() {
    return DateUtils.getDate("2019-10-18");
  }

}
