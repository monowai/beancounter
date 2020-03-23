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

  TrnInput from(List<String> row, Portfolio portfolio) throws ParseException;

  boolean isValid(List<String> row);

}
