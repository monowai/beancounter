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
import org.springframework.stereotype.Component;

/**
 * Date based helper functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@Component
public class DateUtils {

  public ZoneId defaultZone = ZoneId.of("Asia/Singapore");
  public static final String format = "yyyy-MM-dd";
  private final SimpleDateFormat defaultFormatter = new SimpleDateFormat(format);

  public LocalDate getLastMarketDate(LocalDate seedDate, ZoneId targetZone) {
    int days = 1;
    if (!isToday(getDateString(seedDate))) {
      days = 0;
    }
    return getLastMarketDate(seedDate, targetZone, days);
  }

  /**
   * Identify a date to query a market on taking into account timezones and working days.
   * Always takes One from seedDate. Then subtracts a day until it finds a working one.
   * For instance - Sunday 7th in Singapore will result to Friday 5th in New York
   *
   * @param seedDate   usually Today requesting in callers timezone
   * @param targetZone market to locate requestedDate on
   * @return resolved Date
   */
  public LocalDate getLastMarketDate(LocalDate seedDate, ZoneId targetZone, int daysToSubtract) {
    Objects.requireNonNull(seedDate);
    Objects.requireNonNull(targetZone);

    LocalDate result = seedDate.minusDays(daysToSubtract);

    while (!isWorkDay(result)) {
      result = result.minusDays(1);
    }

    return result;
  }

  public LocalDate convert(LocalDate localDate) {
    ZonedDateTime zoned = localDate.atStartOfDay(defaultZone);
    return getDate(zoned.toLocalDate().toString());
  }


  public boolean isWorkDay(LocalDate evaluate) {
    // Naive implementation that is only aware of Western markets
    if (evaluate.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
      return false;
    } else {
      return !evaluate.getDayOfWeek().equals(DayOfWeek.SATURDAY);
    }

    // ToDo: market holidays...
  }

  public LocalDate getDate(String inDate) {
    if (inDate == null || inDate.isBlank()) {
      return null;
    }
    return getDate(inDate, "yyyy-MM-dd");
  }

  public LocalDate getDate(String inDate, String format) {
    if (inDate == null) {
      return null;
    }
    return getLocalDate(inDate, format)
        .atStartOfDay(defaultZone).toLocalDate();

  }

  public LocalDate getLocalDate(String inDate, String dateFormat) {
    if (inDate == null) {
      return null;
    }
    return LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat));
  }

  public String today() {
    return LocalDate.now(defaultZone).toString();
  }

  public void isValid(String inDate) {
    try {
      getDate(inDate);
    } catch (RuntimeException e) {
      throw new BusinessException(String.format("Unable to parse the date %s", inDate));
    }
  }

  public boolean isToday(String inDate) {
    if (inDate == null || inDate.isBlank()) {
      return true; // Null date is BC is "today"
    }
    try {
      Date today = defaultFormatter.parse(today());
      Date compareWith = defaultFormatter.parse(inDate);
      return today.compareTo(compareWith) == 0;
    } catch (ParseException | NumberFormatException e) {
      throw new BusinessException(String.format("Unable to parse the date %s", inDate));
    }
  }

  public String getDateString(LocalDate date) {
    return (date == null ? null : date.toString());
  }
}
