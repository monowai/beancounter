package com.beancounter.common.utils;

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


  private boolean isWorkDay(ZonedDateTime evaluate) {
    // Naive implementation that is only aware of Western markets
    if (evaluate.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
      return false;
    } else {
      return !evaluate.getDayOfWeek().equals(DayOfWeek.SATURDAY);
    }

    // ToDo: market holidays...
  }

  public LocalDate getLocalDate(String inDate, String dateFormat) {
    return LocalDate
        .parse(inDate, DateTimeFormatter.ofPattern(dateFormat));
  }

  public Date getDate(String inDate, String format) {
    return Date.from(
        getLocalDate("2012-10-01", "yyyy-MM-dd")
            .atStartOfDay(TimeZone.getDefault().toZoneId()).toInstant());
  }
}
