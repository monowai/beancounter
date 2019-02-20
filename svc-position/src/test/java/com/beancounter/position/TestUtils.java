package com.beancounter.position;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * @author mikeh
 * @since 2019-02-20
 */
public class TestUtils {

  static Date convert(LocalDate localDate) {
    return java.util.Date.from(localDate.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

}
