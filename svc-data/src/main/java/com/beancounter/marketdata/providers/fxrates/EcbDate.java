package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EcbDate {
  public static final String earliest = "1999-01-04";
  private static LocalDate earliestDate = DateUtils.getDate(earliest);

  public String getValidDate(String inDate) {
    DateUtils.isValid(inDate);

    if (DateUtils.getDate(inDate).isBefore(earliestDate)) {
      return earliest;
    }
    return inDate;
  }

}
