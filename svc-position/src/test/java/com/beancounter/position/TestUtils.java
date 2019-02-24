package com.beancounter.position;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * General helper functions to support unit testing.
 *
 * @author mikeh
 * @since 2019-02-20
 */
class TestUtils {

  static Date convert(LocalDate localDate) {
    return java.util.Date.from(localDate.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

}
