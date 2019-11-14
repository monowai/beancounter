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

  @Test
  void is_TodayAnIso8601String() {
    Calendar calendar = new Calendar.Builder().setInstant(new Date()).build();
    String now = DateUtils.today();
    calendar.get(Calendar.YEAR);
    assertThat(now)
        .isNotNull()
        .startsWith(String.valueOf(calendar.get(Calendar.YEAR)))
        .contains("-" + (calendar.get(Calendar.MONTH) + 1) + "-")
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
    String nullString = null;
    assertThat(DateUtils.getDate(nullString)).isNull();
    assertThat(DateUtils.getLocalDate(nullString, "yyyy-MM-dd")).isNull();
    Date nullDate = null;
    assertThat(DateUtils.getDate(nullDate)).isNull();
  }

  @Test
  void is_LocalDateEqualToToday() {
    assertThat(DateUtils.convert(LocalDate.now())).isEqualTo(DateUtils.today());
  }

  @Test
  void is_FridayFoundFromSundayInSystemDefaultTz() {
    ZonedDateTime sunday = getSunday()
        .toInstant()
        .atZone(ZoneId.systemDefault());

    LocalDate found = DateUtils.getLastMarketDate(sunday, ZoneId.systemDefault());
    assertThat(DateUtils.convert(found)).isEqualTo(getFriday());
  }

  @Test
  void is_WeekendFound() {
    ZonedDateTime zonedDateTime = getSunday()
        .toInstant()
        .atZone(ZoneId.systemDefault());

    assertThat(DateUtils.isWorkDay(zonedDateTime)).isFalse();// Sunday

    zonedDateTime = getSaturday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isFalse(); // Saturday

    zonedDateTime = getFriday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isTrue(); // Friday

    zonedDateTime = getMonday()
        .toInstant()
        .atZone(ZoneId.systemDefault());
    assertThat(DateUtils.isWorkDay(zonedDateTime)).isTrue(); // Monday

  }

  @Test
  void is_MarketDataPriceDateCalculated() {
    String sgToday = "2019-11-01"; // Friday in Singapore
    ZonedDateTime sgDateTime = DateUtils.getDate(sgToday)
        .toInstant()
        .atZone(ZoneId.of("Asia/Singapore"));

    LocalDate marketDataDate = DateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"));
    assertThat(DateUtils.getDate(
        DateUtils.convert(marketDataDate))).isEqualTo("2019-10-31"); // Boo!

    marketDataDate = DateUtils.getLastMarketDate(sgDateTime, ZoneId.of("US/Eastern"), 2);
    assertThat(DateUtils.getDate(
        DateUtils.convert(marketDataDate))).isEqualTo("2019-10-30");


  }

  private Date getMonday() {
    return DateUtils.getDate("2019-10-21");
  }

  private Date getSunday() {
    return DateUtils.getDate("2019-10-20");
  }

  private Date getSaturday() {
    return DateUtils.getDate("2019-10-19");
  }

  private Date getFriday() {
    return DateUtils.getDate("2019-10-18");
  }

}
