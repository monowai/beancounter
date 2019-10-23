package com.beancounter.common.utils;

import com.beancounter.common.exception.BusinessException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Date based helper functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */

public class DateUtils {

  private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

  /**
   * Identify a date to query a market on taking into account timezones and working days.
   * Subtracts a day until it finds a working one, which may prove less than ideal.
   * For instance - Sunday 7th in Singapore will result to Friday 5th in New York
   *
   * @param requestedDate date the user is requesting in _their_ timezone
   * @param targetZone    market to locate requestedDate on
   * @return resolved Date
   */
  public LocalDate getLastMarketDate(ZonedDateTime requestedDate, ZoneId targetZone) {
    Objects.requireNonNull(requestedDate);
    Objects.requireNonNull(targetZone);

    ZonedDateTime result = requestedDate.toLocalDateTime().atZone(targetZone);

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


  private boolean isWorkDay(ZonedDateTime evaluate) {
    // Naive implementation that is only aware of Western markets
    if (evaluate.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
      return false;
    } else {
      return !evaluate.getDayOfWeek().equals(DayOfWeek.SATURDAY);
    }

    // ToDo: market holidays...
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

  public String getDate(Date date) {
    if ( date == null ) {
      return null;
    }
    return simpleDateFormat.format(date);
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
}
