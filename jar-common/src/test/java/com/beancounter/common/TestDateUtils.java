package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;

class TestDateUtils {
  private DateUtils dateUtils = new DateUtils();

  @Test
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder().setInstant(new Date()).build();
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
    assertThrows(BusinessException.class, () -> dateUtils.isValid(invalidDate));
  }

  @Test
  void is_NullIsoDate() {
    String nullString = null;
    assertThat(dateUtils.getDate(nullString)).isNull();
    assertThat(dateUtils.getLocalDate(nullString, "yyyy-MM-dd")).isNull();
    Date nullDate = null;
    assertThat(dateUtils.getDate(nullDate)).isNull();
  }

  @Test
  void is_LocalDateEqualToToday() {
    assertThat(dateUtils.convert(LocalDate.now())).isEqualTo(dateUtils.today());
  }

  @Test
  void is_FridayFoundFromSundayInSystemDefaultTz() {
    ZonedDateTime sunday = getSunday()
        .toInstant()
        .atZone(ZoneId.systemDefault());

    LocalDate found = dateUtils.getLastMarketDate(sunday, ZoneId.systemDefault());
    assertThat(dateUtils.convert(found)).isEqualTo(getFriday());
  }

    @Test
  void is_WeekendFound() {
    ZonedDateTime zonedDateTime = getSunday()
        .toInstant()
        .atZone(ZoneId.systemDefault());

    assertThat(dateUtils.isWorkDay(zonedDateTime)).isFalse();// Sunday

    zonedDateTime = getSaturday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isFalse(); // Saturday

    zonedDateTime = getFriday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isTrue(); // Friday

    zonedDateTime = getMonday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(dateUtils.isWorkDay(zonedDateTime)).isTrue(); // Monday

  }

  private Date getMonday (){
    return dateUtils.getDate("2019-10-21");
  }

  private Date getSunday (){
    return dateUtils.getDate("2019-10-20");
  }

  private Date getSaturday (){
    return dateUtils.getDate("2019-10-19");
  }

  private Date getFriday (){
    return dateUtils.getDate("2019-10-18");
  }

}
