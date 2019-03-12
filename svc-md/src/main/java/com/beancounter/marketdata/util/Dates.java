package com.beancounter.marketdata.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Date based helper functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */
public class Dates {
  /**
   * Converts a yyyy-MM-dd formatted String into a Date.
   *
   * @param date input string
   * @param timeZone optional TZ
   * @return Java Date in the timezone
   */
  public static Date getDate(String date, String timeZone) {
    if (date == null) {
      return null;
    }

    try {

      TimeZone tz;
      if (timeZone == null) {
        tz = TimeZone.getDefault();
      } else {
        tz = TimeZone.getTimeZone(timeZone);
      }
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      sdf.setTimeZone(tz);
      return sdf.parse(date);


    } catch (ParseException e) {
      throw new RuntimeException(String.format("Unable to parse the date %s", date));
    }

  }
}
