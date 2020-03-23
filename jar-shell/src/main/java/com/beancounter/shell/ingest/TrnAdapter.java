package com.beancounter.shell.ingest;

import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import java.text.ParseException;
import java.util.List;

/**
 * Convert the incoming row to a Transaction object.
 *
 * @author mikeh
 * @since 2019-02-10
 */
public interface TrnAdapter {

  TrnInput from(List<Object> row, Portfolio portfolio) throws ParseException;

  boolean isValid(List<Object> row);

}
