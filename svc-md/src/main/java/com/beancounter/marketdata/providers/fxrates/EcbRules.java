package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.utils.DateUtils;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EcbRules {
  public static final String earliest = "1999-01-04";
  private static Date earliestDate;
  private static DateUtils dateUtils = new DateUtils();

  static {
    earliestDate = dateUtils.getDate(earliest);

  }

  public String getValidDate(String inDate) {
    dateUtils.isValid(inDate);
    Date compareTo = dateUtils.getDate(inDate);

    if (compareTo.before(earliestDate)) {
      return earliest;
    }
    return inDate;
  }

}
