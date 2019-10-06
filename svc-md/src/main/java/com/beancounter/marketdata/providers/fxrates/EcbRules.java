package com.beancounter.marketdata.providers.fxrates;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.exception.SystemException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EcbRules {
  public static final String earliest = "1999-01-04";
  private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  private static Date earliestDate;

  static {
    try {
      earliestDate = dateFormat.parse(earliest);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  public String getValidDate(String inDate) {
    Date compareTo;
    try {
      compareTo = dateFormat.parse(inDate);
    } catch (ParseException e) {
      log.error("{} is unparseable", inDate);
      throw new SystemException(String.format("%s is unparseable", inDate));
    }
    if (compareTo.before(earliestDate)) {
      return dateFormat.format(earliestDate);
    }
    return inDate;
  }

  public String date(@NotNull Date inDate) {
    if (inDate == null) {
      throw new BusinessException("Date must not be null");
    }

    return dateFormat.format(inDate);
  }
}
