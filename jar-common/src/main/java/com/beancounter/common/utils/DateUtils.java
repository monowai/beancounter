package com.beancounter.common.utils;

import com.beancounter.common.exception.BusinessException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;

/**
 * Date based helper functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@UtilityClass
public class DateUtils {

  private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

  public LocalDate getLastMarketDate(ZonedDateTime seedDate, ZoneId targetZone) {
    return getLastMarketDate(seedDate, targetZone, 1);
  }

  /**
   * Identify a date to query a market on taking into account timezones and working days.
   * Always takes One from seedDate. Then subtracts a day until it finds a working one.
   * For instance - Sunday 7th in Singapore will result to Friday 5th in New York
   *
   * @param seedDate   usually Today requesting in callers timezeon timezone
   * @param targetZone market to locate requestedDate on
   * @return resolved Date
   */
  public LocalDate getLastMarketDate(ZonedDateTime seedDate, ZoneId targetZone, int days) {
    Objects.requireNonNull(seedDate);
    Objects.requireNonNull(targetZone);

    ZonedDateTime result = seedDate.toLocalDateTime().atZone(targetZone).minusDays(days);

    while (!isWorkDay(result)) {
      result = result.minusDays(1);
    }

    return result.toLocalDate();
  }

  public Date convert(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }


  public boolean isWorkDay(ZonedDateTime evaluate) {
    // Naive implementation that is only aware of Western markets
    if (evaluate.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
      return false;
    } else {
      return !evaluate.getDayOfWeek().equals(DayOfWeek.SATURDAY);
    }

    // ToDo: market holidays...
  }

  public String getDate(Date date) {
    if (date == null) {
      return null;
    }
    return simpleDateFormat.format(date);
  }

  public Date getDate(String inDate) {
    return getDate(inDate, "yyyy-MM-dd");
  }

  public Date getDate(String inDate, String format) {
    if (inDate == null) {
      return null;
    }
    return Date.from(
        getLocalDate(inDate, format)
            .atStartOfDay(TimeZone.getDefault().toZoneId()).toInstant());
  }

  public LocalDate getLocalDate(String inDate, String dateFormat) {
    if (inDate == null) {
      return null;
    }
    return LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat));
  }

  public String today() {
    return getDate(new Date());
  }

  public void isValid(String inDate) {
    try {
      getDate(inDate);
    } catch (RuntimeException e) {
      throw new BusinessException(String.format("Unable to parse the date %s", inDate));
    }
  }

  public static boolean isToday(String inDate) {
    if (inDate == null) {
      return true; // Null date is BC is "today"
    }
    try {
      Date today = simpleDateFormat.parse(today());
      Date compareWith = simpleDateFormat.parse(inDate);
      return today.compareTo(compareWith) == 0;
    } catch (ParseException e) {
      throw new BusinessException(String.format("Unable to parse the date %s", inDate));
    }
  }
}
